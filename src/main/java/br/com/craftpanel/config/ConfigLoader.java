package br.com.craftpanel.config;

import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);

    @SuppressWarnings("unchecked")
    public static PanelConfig load(String configPath) {
        File configFile = new File(configPath);
        if (!configFile.exists()) {
            File fallback = new File("config.yml");
            if (fallback.exists()) {
                configFile = fallback;
            } else {
                exportDefaultConfig(configFile);
            }
        }

        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            root = yaml.load(in);
        } catch (Exception e) {
            LOGGER.error("Failed to parse configuration file at {}", configFile.getAbsolutePath(), e);
            throw new IllegalStateException("Failed to load configuration", e);
        }

        if (root == null) {
            root = Map.of();
        }

        Map<String, Object> serverMap = (Map<String, Object>) root.getOrDefault("server", Map.of());
        Map<String, Object> secMap = (Map<String, Object>) root.getOrDefault("security", Map.of());
        Map<String, Object> storMap = (Map<String, Object>) root.getOrDefault("storage", Map.of());
        Map<String, Object> modMap = (Map<String, Object>) root.getOrDefault("modrinth", Map.of());
        Map<String, Object> monMap = (Map<String, Object>) root.getOrDefault("monitoring", Map.of());
        Map<String, Object> logMap = (Map<String, Object>) root.getOrDefault("logging", Map.of());

        boolean remoteAccess = getBoolean(secMap, "remote_access_enabled", false);
        String host = getString(serverMap, "host", "127.0.0.1");
        if (("0.0.0.0".equals(host) || "::".equals(host)) && !remoteAccess) {
            LOGGER.warn("Host was configured as {} but remote access is disabled. Falling back safely to 127.0.0.1", host);
            host = "127.0.0.1";
        }

        int port = getInt(serverMap, "port", 8080);
        if (port < 1 || port > 65535) {
            port = 8080;
        }

        int sessionTimeout = getInt(secMap, "session_timeout_minutes", 60);
        boolean secureCookies = getBoolean(secMap, "require_secure_cookies", false);
        int maxFailedLogin = getInt(secMap, "max_failed_login_attempts", 5);
        int lockoutMinutes = getInt(secMap, "lockout_duration_minutes", 15);

        String dataDir = getString(storMap, "data_directory", "./data");
        String dbPath = getString(storMap, "database", "./data/database.db");
        String backupDir = getString(storMap, "backup_directory", "./data/backups");
        String serversDir = getString(storMap, "servers_directory", "./data/servers");

        boolean modrinthEnabled = getBoolean(modMap, "enabled", true);
        String modrinthUserAgent = getString(modMap, "user_agent", "CraftPanel-Web/1.0 (contact: minebuilders.com.br/discord)");
        String modrinthApiUrl = getString(modMap, "api_url", "https://api.modrinth.com/v2");

        int refreshInterval = getInt(monMap, "refresh_interval_seconds", 3);
        int maxHistory = getInt(monMap, "max_history_points", 60);
        String logLevel = getString(logMap, "level", "INFO");

        ensureDirectoryExists(dataDir);
        ensureDirectoryExists(backupDir);
        ensureDirectoryExists(serversDir);

        return new PanelConfig(
                new PanelConfig.ServerSettings(host, port),
                new PanelConfig.SecuritySettings(remoteAccess, sessionTimeout, secureCookies, maxFailedLogin, lockoutMinutes),
                new PanelConfig.StorageSettings(dataDir, dbPath, backupDir, serversDir),
                new PanelConfig.ModrinthSettings(modrinthEnabled, modrinthUserAgent, modrinthApiUrl),
                new PanelConfig.MonitoringSettings(refreshInterval, maxHistory),
                new PanelConfig.LoggingSettings(logLevel)
        );
    }

    private static void exportDefaultConfig(File target) {
        try {
            Path parent = target.toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = ConfigLoader.class.getResourceAsStream("/craftpanel.yml")) {
                if (in != null) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.writeString(target.toPath(), "server:\n  host: \"127.0.0.1\"\n  port: 8080\n");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not export default craftpanel.yml to {}", target.getAbsolutePath(), e);
        }
    }

    private static void ensureDirectoryExists(String pathStr) {
        try {
            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create directory: {}", pathStr, e);
        }
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return def;
    }
}
