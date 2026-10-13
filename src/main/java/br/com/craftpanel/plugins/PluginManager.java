package br.com.craftpanel.plugins;

import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.servers.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class PluginManager {
    private final PanelConfig config;

    public PluginManager(PanelConfig config) {
        this.config = config;
    }

    public List<InstalledPlugin> listPlugins(MinecraftServer server) {
        File pluginsDir = getPluginsDir(server);
        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            return List.of();
        }

        File[] files = pluginsDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".jar.disabled"));

        if (files == null) {
            return List.of();
        }

        List<InstalledPlugin> result = new ArrayList<>();
        for (File file : files) {
            result.add(JarMetadataExtractor.extract(file));
        }

        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    public void togglePlugin(MinecraftServer server, String filename) throws IOException {
        File pluginsDir = getPluginsDir(server);
        File file = new File(pluginsDir, filename);

        if (!file.exists()) {
            throw new IllegalArgumentException("Plugin file not found: " + filename);
        }

        File target;
        if (filename.endsWith(".disabled")) {
            target = new File(pluginsDir, filename.substring(0, filename.length() - ".disabled".length()));
        } else {
            target = new File(pluginsDir, filename + ".disabled");
        }

        Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public void deletePlugin(MinecraftServer server, String filename) throws IOException {
        File pluginsDir = getPluginsDir(server);
        File file = new File(pluginsDir, filename);

        if (!file.exists()) {
            return;
        }

        Path backupDir = Paths.get(config.storage().backupDirectory(), "plugins", server.id());
        Files.createDirectories(backupDir);
        Path backupFile = backupDir.resolve(filename + "." + System.currentTimeMillis() + ".bak");
        Files.copy(file.toPath(), backupFile, StandardCopyOption.REPLACE_EXISTING);

        Files.delete(file.toPath());
    }

    public InstalledPlugin installPlugin(MinecraftServer server, String filename, InputStream in) throws IOException {
        File pluginsDir = getPluginsDir(server);
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }

        String safeName = Paths.get(filename).getFileName().toString();
        if (!safeName.toLowerCase().endsWith(".jar")) {
            safeName += ".jar";
        }

        File target = new File(pluginsDir, safeName);
        Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return JarMetadataExtractor.extract(target);
    }

    public File getPluginsDir(MinecraftServer server) {
        return new File(server.serverDir(), "plugins");
    }
}
