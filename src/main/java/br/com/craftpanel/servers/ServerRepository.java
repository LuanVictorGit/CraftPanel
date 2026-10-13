package br.com.craftpanel.servers;

import br.com.craftpanel.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class ServerRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerRepository.class);
    public static final String HOST_SERVER_ID = "local";

    private final DatabaseManager db;

    public ServerRepository(DatabaseManager db) {
        this.db = db;
        ensureSingleHostServer();
    }

    public synchronized void ensureSingleHostServer() {
        String name = "Minecraft Server";
        String software = "Spigot / Paper";
        String version = "26.2";
        int port = 25565;

        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object serverObj = bukkitClass.getMethod("getServer").invoke(null);
            if (serverObj != null) {
                name = (String) bukkitClass.getMethod("getName").invoke(null);
                version = (String) bukkitClass.getMethod("getBukkitVersion").invoke(null);
                software = name;
                port = (int) bukkitClass.getMethod("getPort").invoke(null);
                String motd = (String) bukkitClass.getMethod("getMotd").invoke(null);
                if (motd != null && !motd.isBlank()) {
                    name = motd.replaceAll("§[0-9a-fk-or]", "").trim();
                }
            }
        } catch (Throwable ignored) {
            File props = new File("server.properties");
            if (props.exists()) {
                try (FileInputStream fis = new FileInputStream(props)) {
                    Properties p = new Properties();
                    p.load(fis);
                    port = Integer.parseInt(p.getProperty("server-port", "25565"));
                    String motd = p.getProperty("motd", "Minecraft Server");
                    name = motd.replaceAll("§[0-9a-fk-or]", "").trim();
                } catch (Exception ignored2) {}
            }
        }

        String serverDir = new File(".").getAbsolutePath();
        String jarPath = "./server.jar";
        File dir = new File(".");
        File[] jars = dir.listFiles((d, f) -> f.endsWith(".jar") && !f.equalsIgnoreCase("craftpanel.jar"));
        if (jars != null && jars.length > 0) {
            jarPath = jars[0].getName();
        }

        int totalRamMb = (int) (Runtime.getRuntime().totalMemory() / (1024 * 1024));
        int maxRamMb = (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
        Instant now = Instant.now();

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM servers WHERE id != ?")) {
                del.setString(1, HOST_SERVER_ID);
                del.executeUpdate();
            }

            String sql = """
                INSERT INTO servers (id, name, description, software, minecraft_version, jar_path, server_dir, java_executable, min_memory_mb, max_memory_mb, server_port, jvm_args, auto_start, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    software = excluded.software,
                    minecraft_version = excluded.minecraft_version,
                    server_port = excluded.server_port,
                    updated_at = excluded.updated_at;
            """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, HOST_SERVER_ID);
                stmt.setString(2, name);
                stmt.setString(3, "Host Minecraft Server running CraftPanel");
                stmt.setString(4, software);
                stmt.setString(5, version);
                stmt.setString(6, jarPath);
                stmt.setString(7, serverDir);
                stmt.setString(8, "java");
                stmt.setInt(9, totalRamMb);
                stmt.setInt(10, maxRamMb);
                stmt.setInt(11, port);
                stmt.setString(12, "");
                stmt.setString(13, now.toString());
                stmt.setString(14, now.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to ensure single host server", e);
        }
    }

    public List<MinecraftServer> findAll() {
        String sql = "SELECT id, name, description, software, minecraft_version, jar_path, server_dir, java_executable, min_memory_mb, max_memory_mb, server_port, jvm_args, auto_start, created_at, updated_at FROM servers WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, HOST_SERVER_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return List.of(mapServer(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching host server", e);
        }
        return Collections.emptyList();
    }

    public Optional<MinecraftServer> findById(String id) {
        String sql = "SELECT id, name, description, software, minecraft_version, jar_path, server_dir, java_executable, min_memory_mb, max_memory_mb, server_port, jvm_args, auto_start, created_at, updated_at FROM servers WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, HOST_SERVER_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapServer(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding server", e);
        }
        return Optional.empty();
    }

    public void create(MinecraftServer server) {
        throw new UnsupportedOperationException("Only one server instance exists in CraftPanel");
    }

    public void update(MinecraftServer server) {
        String sql = "UPDATE servers SET name = ?, description = ?, software = ?, minecraft_version = ?, jar_path = ?, server_dir = ?, java_executable = ?, min_memory_mb = ?, max_memory_mb = ?, server_port = ?, jvm_args = ?, auto_start = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, server.name());
            stmt.setString(2, server.description());
            stmt.setString(3, server.software());
            stmt.setString(4, server.minecraftVersion());
            stmt.setString(5, server.jarPath());
            stmt.setString(6, server.serverDir());
            stmt.setString(7, server.javaExecutable());
            stmt.setInt(8, server.minMemoryMb());
            stmt.setInt(9, server.maxMemoryMb());
            stmt.setInt(10, server.serverPort());
            stmt.setString(11, server.jvmArgs());
            stmt.setInt(12, server.autoStart() ? 1 : 0);
            stmt.setString(13, Instant.now().toString());
            stmt.setString(14, HOST_SERVER_ID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating host server", e);
        }
    }

    public void delete(String id) {
        throw new UnsupportedOperationException("The host server instance cannot be deleted");
    }

    private MinecraftServer mapServer(ResultSet rs) throws SQLException {
        return new MinecraftServer(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("software"),
                rs.getString("minecraft_version"),
                rs.getString("jar_path"),
                rs.getString("server_dir"),
                rs.getString("java_executable"),
                rs.getInt("min_memory_mb"),
                rs.getInt("max_memory_mb"),
                rs.getInt("server_port"),
                rs.getString("jvm_args"),
                rs.getInt("auto_start") == 1,
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }
}
