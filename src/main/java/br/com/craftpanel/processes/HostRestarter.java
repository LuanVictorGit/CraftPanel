package br.com.craftpanel.processes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Restarts the server CraftPanel runs in. The plugin stops together with the server, so a small helper JVM
 * ({@link RestartHelper}) is started first: it waits until this process has exited, which frees the port and the world
 * lock, and then starts the server again with this JVM's own command line.
 */
public final class HostRestarter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HostRestarter.class);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final long MAX_LOG_BYTES = 512 * 1024;
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();

    private HostRestarter() {}

    /** Whether a restart was already requested, in which case the server is on its way down. */
    public static boolean isPending() {
        return SCHEDULED.get();
    }

    /** Starts the helper; the caller stops the server afterwards. While a restart is pending, further calls do nothing. */
    public static void schedule(File dataFolder) throws IOException {
        if (!SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        try {
            launchHelper(dataFolder);
        } catch (IOException | RuntimeException e) {
            SCHEDULED.set(false);
            throw e;
        }
    }

    private static void launchHelper(File dataFolder) throws IOException {
        File workDir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        ProcessHandle self = ProcessHandle.current();
        String java = self.info().command().orElseGet(HostRestarter::javaFromHome);
        List<String> serverCommand = currentCommand(java, workDir);

        Path folder = dataFolder.toPath().toAbsolutePath();
        Files.createDirectories(folder);
        File log = folder.resolve("restart.log").toFile();
        if (log.length() > MAX_LOG_BYTES) {
            Files.move(log.toPath(), folder.resolve("restart.log.old"), StandardCopyOption.REPLACE_EXISTING);
        }

        List<String> launch = serverCommand;
        String windowTitle = "-";
        if (WINDOWS) {
            // cmd.exe would reinterpret the arguments; an argument file hands them to java untouched.
            Path argFile = folder.resolve("restart-args.txt");
            writeArgFile(argFile, serverCommand.subList(1, serverCommand.size()));
            launch = List.of(java, "@" + argFile);
            windowTitle = "Minecraft Server";
        }

        ProcessHandle parent = self.parent().orElse(null);
        List<String> helper = new ArrayList<>();
        if (!WINDOWS) {
            // Out of the server's session, so closing its terminal (screen, SSH) does not take the helper down too.
            String detach = firstExecutable("/usr/bin/setsid", "/bin/setsid", "/usr/bin/nohup", "/bin/nohup");
            if (detach != null) {
                helper.add(detach);
            }
        }
        helper.addAll(List.of(java, "-Xmx64m", "-XX:+UseSerialGC", "-Dstdout.encoding=UTF-8",
                "-cp", ownJar(), RestartHelper.class.getName(),
                String.valueOf(self.pid()),
                String.valueOf(parent == null ? -1 : parent.pid()),
                String.valueOf(parent == null ? 0 : parent.info().startInstant().map(Instant::toEpochMilli).orElse(0L)),
                workDir.getPath(), windowTitle, "--"));
        helper.addAll(launch);

        Process process = new ProcessBuilder(helper)
                .directory(workDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                .start();
        process.getOutputStream().close();
        LOGGER.info("Restart helper started (pid {}), progress in {}", process.pid(), log);
    }

    static List<String> currentCommand(String java, File workDir) {
        List<String> command = new ArrayList<>();
        command.add(java);
        // Linux and macOS report the exact arguments; Windows does not, so they are rebuilt from what the JVM reports.
        String[] arguments = ProcessHandle.current().info().arguments().orElse(null);
        if (arguments != null && arguments.length > 0) {
            command.addAll(Arrays.asList(arguments));
            return command;
        }
        List<String> jvmArgs = withoutEnvironmentOptions(ManagementFactory.getRuntimeMXBean().getInputArguments(),
                System.getenv("JDK_JAVA_OPTIONS"), System.getenv("JAVA_TOOL_OPTIONS"), System.getenv("_JAVA_OPTIONS"));
        command.addAll(rebuildArguments(jvmArgs, System.getProperty("java.class.path", ""),
                System.getProperty("sun.java.command", ""), workDir));
        return command;
    }

    static List<String> rebuildArguments(List<String> jvmArgs, String classPath, String javaCommand, File workDir) {
        List<String> arguments = new ArrayList<>(jvmArgs);
        String jar = launchedJar(javaCommand, workDir);
        if (jar != null) {
            arguments.add("-jar");
            arguments.add(jar);
            arguments.addAll(split(javaCommand.substring(jar.length())));
        } else {
            if (!classPath.isEmpty()) {
                arguments.add("-cp");
                arguments.add(classPath);
            }
            arguments.addAll(split(javaCommand));
        }
        return arguments;
    }

    // "java -jar <jar> <args>" is reported as "<jar> <args>"; the jar path itself may contain spaces.
    static String launchedJar(String javaCommand, File workDir) {
        String lower = javaCommand.toLowerCase(Locale.ROOT);
        for (int end = lower.indexOf(".jar"); end >= 0; end = lower.indexOf(".jar", end + 1)) {
            int stop = end + 4;
            if (stop < javaCommand.length() && javaCommand.charAt(stop) != ' ') {
                continue;
            }
            String candidate = javaCommand.substring(0, stop);
            File file = new File(candidate);
            if ((file.isAbsolute() ? file : new File(workDir, candidate)).isFile()) {
                return candidate;
            }
        }
        return null;
    }

    // Options from these variables are reported as JVM arguments too, and the new JVM reads the variables again itself.
    static List<String> withoutEnvironmentOptions(List<String> jvmArgs, String... environmentValues) {
        List<String> arguments = new ArrayList<>(jvmArgs);
        for (String value : environmentValues) {
            if (value != null) {
                split(value).forEach(arguments::remove);
            }
        }
        return arguments;
    }

    // The java launcher reads argument files in the platform encoding; inside quotes, a backslash escapes the next character.
    static void writeArgFile(Path file, List<String> arguments) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String argument : arguments) {
            content.append('"');
            for (char c : argument.toCharArray()) {
                switch (c) {
                    case '\\' -> content.append("\\\\");
                    case '"' -> content.append("\\\"");
                    case '\n' -> content.append("\\n");
                    case '\r' -> content.append("\\r");
                    case '\t' -> content.append("\\t");
                    case '\f' -> content.append("\\f");
                    default -> content.append(c);
                }
            }
            content.append('"').append(System.lineSeparator());
        }
        Files.writeString(file, content, nativeCharset());
    }

    private static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        for (String part : text.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static Charset nativeCharset() {
        try {
            return Charset.forName(System.getProperty("sun.jnu.encoding"));
        } catch (RuntimeException e) {
            return Charset.defaultCharset();
        }
    }

    private static String javaFromHome() {
        return Paths.get(System.getProperty("java.home"), "bin", WINDOWS ? "java.exe" : "java").toString();
    }

    private static String ownJar() throws IOException {
        try {
            return Paths.get(RestartHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (Exception e) {
            throw new IOException("Could not locate the CraftPanel jar", e);
        }
    }

    private static String firstExecutable(String... candidates) {
        for (String candidate : candidates) {
            if (new File(candidate).canExecute()) {
                return candidate;
            }
        }
        return null;
    }
}
