package br.com.craftpanel.processes;

import br.com.craftpanel.CraftPanelPlugin;
import br.com.craftpanel.console.ConsoleCapture;
import br.com.craftpanel.servers.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
    private final Deque<String> recentLineHashes = new ArrayDeque<>(512);
    private final boolean isHostInstance;

    private volatile Process process;
    private volatile BufferedWriter stdinWriter;
    private volatile ProcessState state = ProcessState.STOPPED;
    private volatile Instant startedAt;
    private volatile int lastExitCode = 0;
    private volatile Thread logTailerThread;

    public ServerProcess(MinecraftServer server) {
        this(server, true);
    }

    public ServerProcess(MinecraftServer server, boolean isHostInstance) {
        this.server = server;
        this.buffer = new ConsoleBuffer(10000);
        this.isHostInstance = isHostInstance;

        if (isHostInstance) {
            this.state = ProcessState.RUNNING;
            this.startedAt = Instant.now();
            startLogTailer();
            ConsoleCapture.install(this::onConsoleOutput);
        }
    }

    public synchronized void onConsoleOutput(String line) {
        if (line == null) return;
        String trimmed = line.trim();
        if (!trimmed.isEmpty()) {
            if (recentLineHashes.contains(trimmed)) {
                return;
            }
            if (recentLineHashes.size() >= 500) {
                recentLineHashes.removeFirst();
            }
            recentLineHashes.addLast(trimmed);
        }
        ConsoleLine cl = new ConsoleLine(line, Instant.now(), detectLevel(line));
        buffer.add(cl);
        notifyLine(cl);
    }

    private void startLogTailer() {
        logTailerThread = new Thread(() -> {
            File logFile = new File("logs/latest.log");
            if (!logFile.exists()) {
                logFile = new File(server.serverDir(), "logs/latest.log");
            }

            long filePointer = 0;
            if (logFile.exists()) {
                try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                    long len = raf.length();
                    long seekPos = Math.max(0, len - 10485760);
                    raf.seek(seekPos);
                    if (seekPos > 0) {
                        int ch;
                        while ((ch = raf.read()) != -1) {
                            if (ch == '\n') break;
                        }
                    }
                    ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
                    int b;
                    long lastPointer = raf.getFilePointer();
                    while ((b = raf.read()) != -1) {
                        if (b == '\n') {
                            byte[] bytes = lineBuf.toByteArray();
                            lineBuf.reset();
                            String raw = new String(bytes, StandardCharsets.UTF_8).replace("\r", "");
                            String trimmed = raw.trim();
                            if (!trimmed.isEmpty()) {
                                if (recentLineHashes.size() >= 500) {
                                    recentLineHashes.removeFirst();
                                }
                                recentLineHashes.addLast(trimmed);
                            }
                            buffer.add(new ConsoleLine(raw, Instant.now(), detectLevel(raw)));
                            lastPointer = raf.getFilePointer();
                        } else {
                            lineBuf.write(b);
                        }
                    }
                    filePointer = lastPointer;
                } catch (Exception ignored) {}
            }

            ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
            while (state == ProcessState.RUNNING || state == ProcessState.STARTING) {
                try {
                    if (logFile.exists()) {
                        long currentLen = logFile.length();
                        if (currentLen < filePointer) {
                            filePointer = 0;
                        }
                        if (currentLen > filePointer) {
                            try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                                raf.seek(filePointer);
                                int b;
                                long lastPointer = filePointer;
                                while ((b = raf.read()) != -1) {
                                    if (b == '\n') {
                                        byte[] bytes = lineBuf.toByteArray();
                                        lineBuf.reset();
                                        String raw = new String(bytes, StandardCharsets.UTF_8).replace("\r", "");
                                        onConsoleOutput(raw);
                                        lastPointer = raf.getFilePointer();
                                    } else {
                                        lineBuf.write(b);
                                    }
                                }
                                filePointer = lastPointer;
                            }
                        }
                    }
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        }, "log-tailer-" + server.id());
        logTailerThread.setDaemon(true);
        logTailerThread.start();
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

    public boolean isHostInstance() {
        return isHostInstance;
    }

    public synchronized void start() throws IOException {
        if (isHostInstance) {
            if (state != ProcessState.RUNNING) {
                setState(ProcessState.RUNNING);
            }
            return;
        }

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
            for (String arg : server.jvmArgs().split("\\s+")) {
                if (!arg.isBlank()) {
                    command.add(arg);
                }
            }
        }

        command.add("-jar");
        command.add(jarFile.getName());
        command.add("--nogui");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(serverDir);
        pb.redirectErrorStream(true);

        setState(ProcessState.STARTING);
        this.startedAt = Instant.now();
        this.process = pb.start();
        this.stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        Thread outThread = new Thread(this::readOutput, "server-stdout-" + server.id());
        outThread.setDaemon(true);
        outThread.start();

        process.onExit().thenAccept(p -> {
            this.lastExitCode = p.exitValue();
            if (state != ProcessState.STOPPED) {
                setState(lastExitCode == 0 ? ProcessState.STOPPED : ProcessState.CRASHED);
            }
            this.stdinWriter = null;
            this.process = null;
        });
    }

    public synchronized void sendCommand(String command) throws IOException {
        if (state != ProcessState.RUNNING && state != ProcessState.STARTING) {
            throw new IllegalStateException("Cannot send command to an inactive server");
        }

        ConsoleLine echoLine = new ConsoleLine("> " + command, Instant.now(), "INFO");
        buffer.add(echoLine);
        notifyLine(echoLine);

        if (isHostInstance) {
            dispatchBukkitCommand(command);
            return;
        }

        if (stdinWriter == null) {
            throw new IllegalStateException("Console stdin is not available");
        }

        stdinWriter.write(command);
        stdinWriter.newLine();
        stdinWriter.flush();
    }

    private void dispatchBukkitCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) return;
        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1).trim() : rawCommand.trim();
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object consoleSender = bukkitClass.getMethod("getConsoleSender").invoke(null);
            Method dispatch = bukkitClass.getMethod("dispatchCommand", Class.forName("org.bukkit.command.CommandSender"), String.class);

            CraftPanelPlugin plugin = CraftPanelPlugin.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                try {
                    Method getGlobalRegionScheduler = bukkitClass.getMethod("getGlobalRegionScheduler");
                    Object scheduler = getGlobalRegionScheduler.invoke(null);
                    Method execute = scheduler.getClass().getMethod("execute", org.bukkit.plugin.Plugin.class, Runnable.class);
                    execute.invoke(scheduler, plugin, (Runnable) () -> {
                        try {
                            dispatch.invoke(null, consoleSender, command);
                        } catch (Exception e) {
                            LOGGER.error("Failed to execute Bukkit command", e);
                        }
                    });
                } catch (Throwable fallbackToBukkit) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            dispatch.invoke(null, consoleSender, command);
                        } catch (Exception ex) {
                            LOGGER.error("Failed to execute Bukkit command", ex);
                        }
                    });
                }
            } else {
                dispatch.invoke(null, consoleSender, command);
            }
        } catch (Throwable t) {
            LOGGER.error("Error executing Bukkit command: {}", command, t);
        }
    }

    public synchronized void stop(int timeoutSeconds) {
        setState(ProcessState.STOPPING);
        if (isHostInstance) {
            CraftPanelPlugin plugin = CraftPanelPlugin.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                dispatchBukkitCommand("stop");
            }
            return;
        }

        if (state == ProcessState.STOPPED || process == null) {
            return;
        }

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

    /** Releases panel-side resources (the log tailer) without touching the host server. */
    public void detach() {
        Thread tailer = logTailerThread;
        if (tailer != null) {
            tailer.interrupt();
        }
    }

    public synchronized void kill() {
        if (isHostInstance) {
            CraftPanelPlugin plugin = CraftPanelPlugin.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                dispatchBukkitCommand("stop");
            }
            return;
        }

        setState(ProcessState.STOPPED);
        if (process != null) {
            process.destroyForcibly();
            process = null;
            stdinWriter = null;
        }
    }

    public synchronized void restart(int timeoutSeconds) {
        setState(ProcessState.STOPPING);
        if (isHostInstance) {
            if (HostRestarter.isPending()) {
                return; // the first request already stopped the server
            }
            // The panel stops with the server, so a helper process starts it again once this JVM has exited.
            CraftPanelPlugin plugin = CraftPanelPlugin.getInstance();
            try {
                if (plugin == null || !plugin.isEnabled()) {
                    throw new IOException("CraftPanel is not enabled");
                }
                HostRestarter.schedule(plugin.getDataFolder());
            } catch (IOException | RuntimeException e) {
                setState(ProcessState.RUNNING);
                throw new IllegalStateException("Could not prepare the restart: " + e.getMessage(), e);
            }
            dispatchBukkitCommand("stop");
            return;
        }

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
                onConsoleOutput(line);

                if (state == ProcessState.STARTING && READY_PATTERN.matcher(line).find()) {
                    setState(ProcessState.RUNNING);
                }
            }
        } catch (IOException ignored) {}
    }

    private String detectLevel(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("warn") || lower.contains("warning")) return "WARN";
        if (lower.contains("error") || lower.contains("exception") || lower.contains("fatal")) return "ERROR";
        return "INFO";
    }

    private void setState(ProcessState newState) {
        this.state = newState;
        for (Listener l : listeners) {
            try {
                l.onStateChanged(newState);
            } catch (Exception ignored) {}
        }
    }

    private void notifyLine(ConsoleLine line) {
        for (Listener l : listeners) {
            try {
                l.onLine(line);
            } catch (Exception ignored) {}
        }
    }
}
