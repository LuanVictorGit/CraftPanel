package br.com.craftpanel.servers;

import br.com.craftpanel.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ServerRepository {
    private final DatabaseManager db;

    public ServerRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<MinecraftServer> findAll() {
        List<MinecraftServer> list = new ArrayList<>();
        String sql = "SELECT id, name, description, software, minecraft_version, jar_path, server_dir, java_executable, min_memory_mb, max_memory_mb, server_port, jvm_args, auto_start, created_at, updated_at FROM servers ORDER BY name ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapServer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing servers", e);
        }
        return list;
    }

    public Optional<MinecraftServer> findById(String id) {
        String sql = "SELECT id, name, description, software, minecraft_version, jar_path, server_dir, java_executable, min_memory_mb, max_memory_mb, server_port, jvm_args, auto_start, created_at, updated_at FROM servers WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapServer(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding server by id", e);
        }
        return Optional.empty();
    }

    public void create(MinecraftServer server) {
        String sql = "INSERT INTO servers (id, name, description, software, minecraft_version, jar_path, server_dir, java_executable, min_memory_mb, max_memory_mb, server_port, jvm_args, auto_start, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, server.id());
            stmt.setString(2, server.name());
            stmt.setString(3, server.description());
            stmt.setString(4, server.software());
            stmt.setString(5, server.minecraftVersion());
            stmt.setString(6, server.jarPath());
            stmt.setString(7, server.serverDir());
            stmt.setString(8, server.javaExecutable());
            stmt.setInt(9, server.minMemoryMb());
            stmt.setInt(10, server.maxMemoryMb());
            stmt.setInt(11, server.serverPort());
            stmt.setString(12, server.jvmArgs());
            stmt.setInt(13, server.autoStart() ? 1 : 0);
            stmt.setString(14, server.createdAt().toString());
            stmt.setString(15, server.updatedAt().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating server", e);
        }
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
            stmt.setString(14, server.id());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating server", e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM servers WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting server", e);
        }
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
