package br.com.craftpanel.plugins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRemovalQueueTest {

    @Test
    void resumeDeletesJarsLeftByAPreviousRunAndForgetsThem(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("Broken.jar"), "x");
        Path list = dir.resolve("pending.txt");
        Files.write(list, List.of(jar.toString()));

        new PluginRemovalQueue(list).resume();

        assertFalse(Files.exists(jar));
        assertFalse(Files.exists(list));
    }

    @Test
    void helperDeletesListedJarsAndKeepsOtherFiles(@TempDir Path dir) throws Exception {
        Path listed = Files.writeString(dir.resolve("Broken.jar"), "x");
        Path other = Files.writeString(dir.resolve("Healthy.jar"), "x");
        Path list = dir.resolve("pending.txt");
        Files.write(list, List.of(listed.toString(), dir.resolve("AlreadyGone.jar").toString()));

        PluginRemovalHelper.apply(list);

        assertFalse(Files.exists(listed));
        assertTrue(Files.exists(other));
        assertFalse(Files.exists(list));
    }

    @Test
    void reinstallingAPluginCancelsItsPendingRemoval(@TempDir Path dir) throws Exception {
        Path jar = Files.writeString(dir.resolve("Broken.jar"), "x");
        Path list = dir.resolve("pending.txt");
        Files.write(list, List.of(jar.toAbsolutePath().normalize().toString(), dir.resolve("Other.jar").toAbsolutePath().normalize().toString()));

        PluginRemovalQueue queue = new PluginRemovalQueue(list);
        assertTrue(queue.contains(jar));
        queue.remove(jar);

        assertFalse(queue.contains(jar));
        assertTrue(queue.contains(dir.resolve("Other.jar")));
    }
}
