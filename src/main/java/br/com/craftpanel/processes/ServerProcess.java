package br.com.craftpanel.processes;

import br.com.craftpanel.servers.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ServerProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerProcess.class);
    private static final Pattern READY_PATTERN = Pattern.compile(
            "Done \\([0-9.]+s\\)|For help, type \"help\"|Server started|Listening on",
            Pattern.CASE_INSENSITIVE
    );

    public interface Listener {
        void onLine(ConsoleLine line);
        void onStateChanged(ProcessState newState);
    }

    private final MinecraftServer server;
    private final ConsoleBuffer buffer;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile Process process;
    private volatile BufferedWriter stdinWriter;
    private volatile ProcessState state = ProcessState.STOPPED;
    private volatile Instant startedAt;
    private volatile int lastExitCode = 0;

    public ServerProcess(MinecraftServer server) {
        this.server = server;
        this.buffer = new ConsoleBuffer(1500);
    }

    public synchronized void addListener(Listener listener) {
        listeners.add(listener);
    }

    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ProcessState getState() {
        return state;
    }

    public ConsoleBuffer getBuffer() {
        return buffer;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public int getLastExitCode() {
        return lastExitCode;
    }

    public Duration getUptime() {
        if (state == ProcessState.RUNNING || state == ProcessState.STARTING) {
            if (startedAt != null) {
                return Duration.between(startedAt, Instant.now());
            }
        }
        return Duration.ZERO;
    }

    public synchronized void start() throws IOException {
        if (state == ProcessState.RUNNING || state == ProcessState.STARTING) {
            throw new IllegalStateException("Server is already running or starting");
        }

        File jarFile = new File(server.jarPath());
        if (!jarFile.exists()) {
            throw new IllegalStateException("Server JAR file not found at " + server.jarPath());
        }

        File serverDir = new File(server.serverDir());
        if (!serverDir.exists()) {
            serverDir.mkdirs();
        }

        List<String> command = new ArrayList<>();
        command.add(server.javaExecutable());
        command.add("-Xms" + server.minMemoryMb() + "M");
        command.add("-Xmx" + server.maxMemoryMb() + "M");

        if (server.jvmArgs() != null && !server.jvmArgs().isBlank()) {
            String[] args = server.jvmArgs().trim().split("\\s+");
            for (String arg : args) {
                if (!arg.isBlank()) {
                    command.add(arg);
                }
            }
        }

        command.add("-jar");
        command.add(jarFile.getAbsolutePath());
        command.add("nogui");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(serverDir);
        pb.redirectErrorStream(true);

        this.process = pb.start();
        this.stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.startedAt = Instant.now();
        setState(ProcessState.STARTING);

        Thread readerThread = new Thread(this::readOutput, "server-console-" + server.id());
        readerThread.setDaemon(true);
        readerThread.start();

        process.onExit().thenAccept(proc -> {
            this.lastExitCode = proc.exitValue();
            LOGGER.info("Server {} exited with code {}", server.name(), lastExitCode);
            if (this.state == ProcessState.STOPPING || lastExitCode == 0) {
                setState(ProcessState.STOPPED);
            } else {
                setState(ProcessState.CRASHED);
            }
            this.stdinWriter = null;
            this.process = null;
        });
    }

    public synchronized void sendCommand(String command) throws IOException {
        if (state != ProcessState.RUNNING && state != ProcessState.STARTING) {
            throw new IllegalStateException("Cannot send command to an inactive server");
        }
        if (stdinWriter == null) {
            throw new IllegalStateException("Console stdin is not available");
        }

        stdinWriter.write(command);
        stdinWriter.newLine();
        stdinWriter.flush();

        ConsoleLine echoLine = new ConsoleLine("> " + command, Instant.now(), "INFO");
        buffer.add(echoLine);
        notifyLine(echoLine);
    }

    public synchronized void stop(int timeoutSeconds) {
        if (state == ProcessState.STOPPED || process == null) {
            return;
        }

        setState(ProcessState.STOPPING);
        try {
            if (stdinWriter != null) {
                stdinWriter.write("stop");
                stdinWriter.newLine();
                stdinWriter.flush();
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to write 'stop' to server stdin, destroying forcibly", e);
        }

        Process proc = this.process;
        Thread stopWaiter = new Thread(() -> {
            try {
                if (proc != null && !proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    LOGGER.warn("Server {} did not stop gracefully in {}s, killing forcibly", server.name(), timeoutSeconds);
                    proc.destroyForcibly();
                }
            } catch (InterruptedException e) {
                if (proc != null) {
                    proc.destroyForcibly();
                }
            }
        }, "server-stop-" + server.id());
        stopWaiter.setDaemon(true);
        stopWaiter.start();
    }

    public synchronized void kill() {
        setState(ProcessState.STOPPED);
        if (process != null) {
            process.destroyForcibly();
            process = null;
            stdinWriter = null;
        }
    }

    public synchronized void restart(int timeoutSeconds) {
        if (state == ProcessState.STOPPED) {
            try {
                start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to start server", e);
            }
            return;
        }

        stop(timeoutSeconds);
        Thread restarter = new Thread(() -> {
            try {
                int waited = 0;
                while (state != ProcessState.STOPPED && state != ProcessState.CRASHED && waited < timeoutSeconds + 5) {
                    Thread.sleep(1000);
                    waited++;
                }
                if (state == ProcessState.STOPPED || state == ProcessState.CRASHED) {
                    start();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to restart server {}", server.name(), e);
            }
        }, "server-restart-" + server.id());
        restarter.setDaemon(true);
        restarter.start();
    }

    private void readOutput() {
        Process proc = this.process;
        if (proc == null) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String level = detectLevel(line);
                ConsoleLine consoleLine = new ConsoleLine(line, Instant.now(), level);
                buffer.add(consoleLine);
                notifyLine(consoleLine);

                if (state == ProcessState.STARTING && READY_PATTERN.matcher(line).find()) {
                    setState(ProcessState.RUNNING);
                }
            }
        } catch (IOException ignored) {}
    }

    private String detectLevel(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("error") || lower.contains("exception") || lower.contains("severe")) {
            return "ERROR";
        }
        if (lower.contains("warn") || lower.contains("warning")) {
            return "WARN";
        }
        return "INFO";
    }

    private void setState(ProcessState newState) {
        this.state = newState;
        for (Listener listener : listeners) {
            try {
                listener.onStateChanged(newState);
            } catch (Exception e) {
                LOGGER.error("Error notifying state change listener", e);
            }
        }
    }

    private void notifyLine(ConsoleLine line) {
        for (Listener listener : listeners) {
            try {
                listener.onLine(line);
            } catch (Exception e) {
                LOGGER.error("Error notifying line listener", e);
            }
        }
    }
}
