package br.com.craftpanel.plugins;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs in a JVM of its own: waits for the server process to exit, then deletes the jars listed by
 * {@link PluginRemovalQueue}. Only the CraftPanel jar is on its class path, so it uses nothing but JDK classes.
 *
 * <p>Arguments: {@code serverPid listFile}
 */
public final class PluginRemovalHelper {
    private static final int DELETE_ATTEMPTS = 20;

    private PluginRemovalHelper() {}

    public static void main(String[] args) throws Exception {
        long serverPid = Long.parseLong(args[0]);
        Path listFile = Paths.get(args[1]);

        Optional<ProcessHandle> server = ProcessHandle.of(serverPid);
        while (server.isPresent() && server.get().isAlive()) {
            Thread.sleep(100);
        }
        apply(listFile);
    }

    static void apply(Path listFile) throws IOException, InterruptedException {
        if (!Files.isRegularFile(listFile)) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        for (String line : Files.readAllLines(listFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !delete(Paths.get(line.trim()))) {
                remaining.add(line.trim());
            }
        }
        if (remaining.isEmpty()) {
            Files.deleteIfExists(listFile);
        } else {
            Files.write(listFile, remaining, StandardCharsets.UTF_8);
        }
    }

    private static boolean delete(Path jar) throws InterruptedException {
        for (int attempt = 0; attempt < DELETE_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(jar);
                return true;
            } catch (IOException stillClosing) {
                Thread.sleep(100);
            }
        }
        return false;
    }
}
