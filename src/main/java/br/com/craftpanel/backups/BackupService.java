package br.com.craftpanel.backups;

import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.database.DatabaseManager;
import br.com.craftpanel.servers.MinecraftServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupService {
    private final DatabaseManager db;
    private final PanelConfig config;

    public BackupService(DatabaseManager db, PanelConfig config) {
        this.db = db;
        this.config = config;
    }

    public List<BackupMetadata> listBackups(String serverId) {
        List<BackupMetadata> list = new ArrayList<>();
        String sql = "SELECT id, server_id, filename, file_size_bytes, checksum, created_by, notes, created_at FROM backups WHERE server_id = ? ORDER BY created_at DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapBackup(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing backups", e);
        }
        return list;
    }

    public Optional<BackupMetadata> getBackup(String id) {
        String sql = "SELECT id, server_id, filename, file_size_bytes, checksum, created_by, notes, created_at FROM backups WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBackup(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getting backup", e);
        }
        return Optional.empty();
    }

    public BackupMetadata createBackup(MinecraftServer server, String createdBy, String notes, Consumer<Integer> progressConsumer) throws Exception {
        String id = UUID.randomUUID().toString();
        String filename = server.name().replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + System.currentTimeMillis() + ".zip";

        Path backupDir = Paths.get(config.storage().backupDirectory(), server.id());
        Files.createDirectories(backupDir);
        Path targetZip = backupDir.resolve(filename);

        Path sourceDir = Paths.get(server.serverDir());
        List<Path> allFiles;
        try (var walk = Files.walk(sourceDir)) {
            allFiles = walk.filter(p -> !Files.isDirectory(p))
                    .filter(p -> !p.toString().endsWith(".lock") && !p.toString().endsWith(".download"))
                    .toList();
        }

        int total = allFiles.size();
        int processed = 0;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (FileOutputStream fos = new FileOutputStream(targetZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            byte[] buf = new byte[16384];
            for (Path path : allFiles) {
                String relative = sourceDir.relativize(path).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(relative);
                zos.putNextEntry(entry);

                try (InputStream fis = Files.newInputStream(path)) {
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                        digest.update(buf, 0, len);
                    }
                }
                zos.closeEntry();
                processed++;
                if (progressConsumer != null && total > 0) {
                    progressConsumer.accept((processed * 100) / total);
                }
            }
        }

        long fileSize = Files.size(targetZip);
        String checksum = HexFormat.of().formatHex(digest.digest());
        Instant now = Instant.now();

        BackupMetadata metadata = new BackupMetadata(
                id,
                server.id(),
                filename,
                fileSize,
                checksum,
                createdBy != null ? createdBy : "admin",
                notes != null ? notes : "",
                now
        );

        saveBackupRecord(metadata);
        return metadata;
    }

    public void restoreBackup(MinecraftServer server, String backupId, Consumer<Integer> progressConsumer) throws Exception {
        BackupMetadata meta = getBackup(backupId).orElseThrow(() -> new IllegalArgumentException("Backup not found"));
        Path backupFile = Paths.get(config.storage().backupDirectory(), server.id(), meta.filename());

        if (!Files.exists(backupFile)) {
            throw new IllegalStateException("Backup archive file is missing from disk");
        }

        Path targetDir = Paths.get(server.serverDir());
        Files.createDirectories(targetDir);

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(backupFile.toFile()))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir.toAbsolutePath().normalize())) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Path parent = resolved.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(resolved.toFile())) {
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
        if (progressConsumer != null) {
            progressConsumer.accept(100);
        }
    }

    public void deleteBackup(String backupId) {
        Optional<BackupMetadata> meta = getBackup(backupId);
        if (meta.isPresent()) {
            Path file = Paths.get(config.storage().backupDirectory(), meta.get().serverId(), meta.get().filename());
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {}

            String sql = "DELETE FROM backups WHERE id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, backupId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error deleting backup", e);
            }
        }
    }

    public File getBackupFile(String backupId) {
        BackupMetadata meta = getBackup(backupId).orElseThrow(() -> new IllegalArgumentException("Backup not found"));
        File file = new File(Paths.get(config.storage().backupDirectory(), meta.serverId(), meta.filename()).toString());
        if (!file.exists()) {
            throw new IllegalStateException("File not found on disk");
        }
        return file;
    }

    private void saveBackupRecord(BackupMetadata meta) {
        String sql = "INSERT INTO backups (id, server_id, filename, file_size_bytes, checksum, created_by, notes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meta.id());
            stmt.setString(2, meta.serverId());
            stmt.setString(3, meta.filename());
            stmt.setLong(4, meta.fileSizeBytes());
            stmt.setString(5, meta.checksum());
            stmt.setString(6, meta.createdBy());
            stmt.setString(7, meta.notes());
            stmt.setString(8, meta.createdAt().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error inserting backup record", e);
        }
    }

    private BackupMetadata mapBackup(ResultSet rs) throws SQLException {
        return new BackupMetadata(
                rs.getString("id"),
                rs.getString("server_id"),
                rs.getString("filename"),
                rs.getLong("file_size_bytes"),
                rs.getString("checksum"),
                rs.getString("created_by"),
                rs.getString("notes"),
                Instant.parse(rs.getString("created_at"))
        );
    }
}
