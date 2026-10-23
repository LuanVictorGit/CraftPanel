package br.com.craftpanel.processes;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Started by {@link HostRestarter} in a JVM of its own: waits until the old server process has exited, then starts the
 * server again. Only the CraftPanel jar is on its class path, so it uses nothing but JDK classes. Its output goes to
 * plugins/CraftPanel/restart.log.
 *
 * <p>Arguments: {@code serverPid parentPid parentStartMillis workDir windowTitle|- -- command...}
 */
public final class RestartHelper {
    private static final Set<String> SHELLS = Set.of("cmd", "powershell", "pwsh", "bash", "sh", "dash", "zsh", "ksh", "ash", "fish");
    private static final long STOP_TIMEOUT_MINUTES = 5;
    private static final long IDLE_GRACE_MS = 5_000;
    private static final long SCRIPT_WAIT_MS = 60_000;
    private static final long WATCH_SECONDS = 20;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RestartHelper() {}

    public static void main(String[] args) throws Exception {
        long serverPid = Long.parseLong(args[0]);
        long parentPid = Long.parseLong(args[1]);
        long parentStart = Long.parseLong(args[2]);
        File workDir = new File(args[3]);
        String windowTitle = args[4];
        List<String> command = new ArrayList<>(Arrays.asList(args).subList(6, args.length));

        log("Restart requested, waiting for the server (pid " + serverPid + ") to stop");
        Optional<ProcessHandle> server = ProcessHandle.of(serverPid);
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(STOP_TIMEOUT_MINUTES);
        while (server.isPresent() && server.get().isAlive()) {
            if (System.nanoTime() > deadline) {
                log("The server did not stop within " + STOP_TIMEOUT_MINUTES + " minutes; restart cancelled");
                return;
            }
            Thread.sleep(200);
        }
        Instant stoppedAt = Instant.now();
        log("The server stopped");

        if (restartedByLauncher(parentPid, parentStart, stoppedAt)) {
            log("The script that started the server has started it again; nothing else to do");
            return;
        }

        log("Starting the server: " + String.join(" ", command));
        Instant launchedAt = Instant.now();
        if ("-".equals(windowTitle)) {
            // No terminal to return to: the panel console keeps working through logs/latest.log.
            Process process = new ProcessBuilder(command)
                    .directory(workDir)
                    .redirectInput(new File("/dev/null"))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectErrorStream(true)
                    .start();
            watch(process.toHandle());
            return;
        }

        // "start" opens the server in a new console window, as when it is started by hand.
        List<String> start = new ArrayList<>(List.of("cmd.exe", "/c", "start", quote(windowTitle), "/D", quote(workDir.getPath())));
        command.forEach(part -> start.add(quote(part)));
        Process starter = new ProcessBuilder(start)
                .directory(workDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();
        starter.getOutputStream().close();
        if (!starter.waitFor(30, TimeUnit.SECONDS) || starter.exitValue() != 0) {
            log("cmd.exe could not start the server" + (starter.isAlive() ? "" : " (exit code " + starter.exitValue() + ")"));
            return;
        }
        Optional<ProcessHandle> started = findStarted(command.get(0), launchedAt);
        if (started.isPresent()) {
            watch(started.get());
        } else {
            log("Server launched in a new window");
        }
    }

    // A launcher script with a loop starts the server again by itself; a second server would fight it for the port.
    private static boolean restartedByLauncher(long parentPid, long parentStart, Instant stoppedAt) throws InterruptedException {
        ProcessHandle parent = ProcessHandle.of(parentPid)
                .filter(p -> parentStart == 0 || p.info().startInstant().map(Instant::toEpochMilli).orElse(parentStart) == parentStart)
                .orElse(null);
        boolean shell = parent != null && SHELLS.contains(name(parent));
        Instant since = stoppedAt.minusSeconds(2);
        long begin = System.nanoTime();
        while (parent != null && parent.isAlive()) {
            List<ProcessHandle> fresh = parent.children()
                    .filter(child -> child.info().startInstant().map(s -> !s.isBefore(since)).orElse(true))
                    .toList();
            if (fresh.stream().anyMatch(child -> name(child).startsWith("java"))) {
                return true;
            }
            long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
            // A script that is still running something (sleep, timeout) may be about to loop; anything else gets a short grace.
            boolean scriptBusy = shell && !fresh.isEmpty();
            if ((!scriptBusy && waited >= IDLE_GRACE_MS) || waited >= SCRIPT_WAIT_MS) {
                return false;
            }
            Thread.sleep(250);
        }
        Thread.sleep(1000);
        return false;
    }

    private static Optional<ProcessHandle> findStarted(String java, Instant launchedAt) throws InterruptedException {
        long self = ProcessHandle.current().pid();
        Instant since = launchedAt.minusSeconds(1);
        for (int attempt = 0; attempt < 40; attempt++) {
            Optional<ProcessHandle> found = ProcessHandle.allProcesses()
                    .filter(p -> p.pid() != self)
                    .filter(p -> p.info().command().map(java::equalsIgnoreCase).orElse(false))
                    .filter(p -> p.info().startInstant().map(s -> !s.isBefore(since)).orElse(false))
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }
            Thread.sleep(250);
        }
        return Optional.empty();
    }

    private static void watch(ProcessHandle process) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WATCH_SECONDS);
        while (process.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(250);
        }
        log(process.isAlive()
                ? "Server started (pid " + process.pid() + ")"
                : "The server exited right after starting; see logs/latest.log");
    }

    private static String name(ProcessHandle process) {
        String command = process.info().command().orElse("");
        String name = command.substring(Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\')) + 1).toLowerCase(Locale.ROOT);
        return name.endsWith(".exe") ? name.substring(0, name.length() - 4) : name;
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static void log(String message) {
        System.out.println("[" + LocalDateTime.now().format(TIME) + "] " + message);
    }
}
