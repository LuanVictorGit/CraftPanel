package br.com.craftpanel.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plugin jars that were deleted in the panel but are still locked by the running server, kept in a file so the removal
 * survives a crash. {@link PluginRemovalHelper} applies the list once this JVM has exited, because a shutdown hook
 * runs while the jar is still open and {@code File.deleteOnExit} would fail exactly when it is needed.
 */
final class PluginRemovalQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginRemovalQueue.class);

    private final Path listFile;
    private boolean helperStarted;

    PluginRemovalQueue(Path listFile) {
        this.listFile = listFile.toAbsolutePath().normalize();
    }

    /** Called on startup: finishes removals a previous run could not complete. */
    synchronized void resume() {
        Set<Path> pending = read();
        if (pending.isEmpty()) {
            return;
        }
        Set<Path> remaining = new LinkedHashSet<>();
        for (Path jar : pending) {
            try {
                PluginJarReleaser.releaseIfPresent(jar.toFile());
                Files.deleteIfExists(jar);
            } catch (IOException | RuntimeException locked) {
                remaining.add(jar);
            }
        }
        write(remaining);
        if (!remaining.isEmpty()) {
            startHelper();
        }
    }

    synchronized void add(Path jar) throws IOException {
        Set<Path> pending = read();
        pending.add(normalize(jar));
        writeOrThrow(pending);
        startHelper();
    }

    synchronized void remove(Path jar) {
        Set<Path> pending = read();
        if (pending.remove(normalize(jar))) {
            write(pending);
        }
    }

    synchronized boolean contains(Path jar) {
        return read().contains(normalize(jar));
    }

    private static Path normalize(Path jar) {
        return jar.toAbsolutePath().normalize();
    }

    private Set<Path> read() {
        Set<Path> pending = new LinkedHashSet<>();
        if (Files.isRegularFile(listFile)) {
            try {
                for (String line : Files.readAllLines(listFile, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        pending.add(Paths.get(line.trim()));
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read {}: {}", listFile, e.toString());
            }
        }
        return pending;
    }

    private void write(Set<Path> pending) {
        try {
            writeOrThrow(pending);
        } catch (IOException e) {
            LOGGER.warn("Could not update {}: {}", listFile, e.toString());
        }
    }

    private void writeOrThrow(Set<Path> pending) throws IOException {
        if (pending.isEmpty()) {
            Files.deleteIfExists(listFile);
            return;
        }
        Files.createDirectories(listFile.getParent());
        List<String> lines = new ArrayList<>(pending.size());
        pending.forEach(path -> lines.add(path.toString()));
        Files.write(listFile, lines, StandardCharsets.UTF_8);
    }

    // The helper reads the list when the server exits, so later additions and cancellations are honoured by one helper.
    private void startHelper() {
        if (helperStarted) {
            return;
        }
        try {
            String java = ProcessHandle.current().info().command()
                    .orElseGet(() -> Paths.get(System.getProperty("java.home"), "bin", "java").toString());
            String ownJar = Paths.get(PluginRemovalHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            new ProcessBuilder(java, "-Xmx32m", "-XX:+UseSerialGC", "-cp", ownJar, PluginRemovalHelper.class.getName(),
                    String.valueOf(ProcessHandle.current().pid()), listFile.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectInput(ProcessBuilder.Redirect.from(new File(isWindows() ? "NUL" : "/dev/null")))
                    .start();
            helperStarted = true;
        } catch (Exception e) {
            // The list is still on disk and the next startup retries it.
            LOGGER.warn("Could not start the plugin removal helper: {}", e.toString());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
