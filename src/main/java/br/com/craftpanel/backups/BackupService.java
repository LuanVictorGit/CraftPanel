package br.com.craftpanel.backups;

import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.database.DatabaseManager;
import br.com.craftpanel.servers.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
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
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);
    private static final String PENDING_RESTORE_FILE = "pending-restore.properties";

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

        Path sourceDir = Paths.get(server.serverDir()).toAbsolutePath().normalize();
        List<Path> panelData = panelDataPaths(config, sourceDir);
        List<Path> allFiles = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return isPanelData(dir, panelData) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (attrs.isRegularFile() && !name.endsWith(".lock") && !name.endsWith(".download")
                        && !name.endsWith(".part") && !isPanelData(file, panelData)) {
                    allFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                skipped.add(sourceDir.relativize(file).toString().replace('\\', '/'));
                return FileVisitResult.CONTINUE;
            }
        });

        int total = allFiles.size();
        int processed = 0;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (FileOutputStream fos = new FileOutputStream(targetZip.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            byte[] buf = new byte[16384];
            for (Path path : allFiles) {
                String relative = sourceDir.relativize(path).toString().replace('\\', '/');
                boolean entryOpen = false;
                try (InputStream fis = Files.newInputStream(path)) {
                    // Read before opening the entry so a locked or vanished file is skipped cleanly.
                    int len = fis.read(buf);
                    zos.putNextEntry(new ZipEntry(relative));
                    entryOpen = true;
                    while (len > 0) {
                        zos.write(buf, 0, len);
                        digest.update(buf, 0, len);
                        len = fis.read(buf);
                    }
                    zos.closeEntry();
                    entryOpen = false;
                } catch (IOException e) {
                    if (entryOpen) {
                        throw e;
                    }
                    skipped.add(relative);
                }
                processed++;
                if (progressConsumer != null && total > 0) {
                    progressConsumer.accept((processed * 100) / total);
                }
            }
        } catch (Exception e) {
            Files.deleteIfExists(targetZip);
            throw e;
        }

        if (!skipped.isEmpty()) {
            LOGGER.warn("Backup {} skipped {} unreadable file(s): {}", filename, skipped.size(), String.join(", ", skipped));
            String skippedNote = "Arquivos ignorados (em uso ou ilegíveis): " + String.join(", ", skipped.subList(0, Math.min(10, skipped.size())))
                    + (skipped.size() > 10 ? " (+" + (skipped.size() - 10) + ")" : "");
            notes = notes == null || notes.isBlank() ? skippedNote : notes + " | " + skippedNote;
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
        Path backupFile = resolveBackupArchive(server, backupId);
        Path targetDir = Paths.get(server.serverDir()).toAbsolutePath().normalize();
        Files.createDirectories(targetDir);

        RestoreResult result = extractArchive(backupFile, targetDir, panelDataPaths(config, targetDir), false);
        if (!result.failed().isEmpty()) {
            throw new IOException("Restored " + result.restored() + " file(s), but could not write: " + String.join(", ", result.failed()));
        }
        if (progressConsumer != null) {
            progressConsumer.accept(100);
        }
    }

    /**
     * Unzipping over the running host races its shutdown save and the JVM exit kills it half way, so a host
     * restore is only staged here and applied by {@link #applyPendingRestore} on the next server start.
     */
    public void scheduleRestore(MinecraftServer server, String backupId, String requestedBy) throws IOException {
        Path backupFile = resolveBackupArchive(server, backupId);
        Properties marker = new Properties();
        marker.setProperty("backupFile", backupFile.toAbsolutePath().normalize().toString());
        marker.setProperty("backupId", backupId);
        marker.setProperty("requestedBy", requestedBy != null ? requestedBy : "admin");
        marker.setProperty("requestedAt", Instant.now().toString());

        Path markerFile = Paths.get(config.storage().dataDirectory(), PENDING_RESTORE_FILE);
        Files.createDirectories(markerFile.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(markerFile)) {
            marker.store(out, "CraftPanel: backup restore applied on the next server start");
        }
    }

    /**
     * Applies a restore staged by {@link #scheduleRestore}. Called from the plugin's onLoad, before any world is
     * loaded. Jars are skipped because the server and every plugin already hold theirs open at that point.
     */
    public static void applyPendingRestore(PanelConfig config, Path serverDir) {
        Path markerFile = Paths.get(config.storage().dataDirectory(), PENDING_RESTORE_FILE);
        if (!Files.exists(markerFile)) {
            return;
        }
        try {
            Properties marker = new Properties();
            try (InputStream in = Files.newInputStream(markerFile)) {
                marker.load(in);
            }
            Path backupFile = Paths.get(marker.getProperty("backupFile", ""));
            Path targetDir = serverDir.toAbsolutePath().normalize();
            LOGGER.warn("Applying backup restore requested by {} from {}", marker.getProperty("requestedBy"), backupFile);

            RestoreResult result = extractArchive(backupFile, targetDir, panelDataPaths(config, targetDir), true);
            LOGGER.warn("Backup restore finished: {} file(s) restored, {} skipped (jars and CraftPanel data)", result.restored(), result.skipped());
            if (!result.failed().isEmpty()) {
                LOGGER.error("Backup restore could not write {} file(s): {}", result.failed().size(), String.join(", ", result.failed()));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to apply pending backup restore", e);
        } finally {
            try {
                Files.deleteIfExists(markerFile);
            } catch (IOException e) {
                LOGGER.error("Could not remove {}; the restore would be applied again on next start", markerFile, e);
            }
        }
    }

    private Path resolveBackupArchive(MinecraftServer server, String backupId) {
        BackupMetadata meta = getBackup(backupId).orElseThrow(() -> new IllegalArgumentException("Backup not found"));
        Path backupFile = Paths.get(config.storage().backupDirectory(), server.id(), meta.filename());
        if (!Files.exists(backupFile)) {
            throw new IllegalStateException("Backup archive file is missing from disk");
        }
        return backupFile;
    }

    private record RestoreResult(int restored, int skipped, List<String> failed) {}

    private static RestoreResult extractArchive(Path backupFile, Path targetDir, List<Path> panelData, boolean skipJars) throws IOException {
        int restored = 0;
        int skipped = 0;
        List<String> failed = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(backupFile.toFile()))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                // Older backups also contain CraftPanel's database and previous backups; never write those back.
                if (!resolved.startsWith(targetDir) || isPanelData(resolved, panelData)
                        || (skipJars && entry.getName().toLowerCase().endsWith(".jar"))) {
                    skipped++;
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }
                try {
                    Files.createDirectories(resolved.getParent());
                    try (OutputStream out = Files.newOutputStream(resolved)) {
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                    restored++;
                } catch (IOException e) {
                    failed.add(entry.getName());
                }
            }
        }
        return new RestoreResult(restored, skipped, failed);
    }

    // CraftPanel's own data lives inside the server folder by default: archiving it makes every backup contain
    // all previous ones, and restoring it would overwrite the live database.
    private static List<Path> panelDataPaths(PanelConfig config, Path serverDir) {
        Path root = serverDir.toAbsolutePath().normalize();
        List<Path> paths = new ArrayList<>();
        for (String dir : List.of(config.storage().dataDirectory(), config.storage().backupDirectory())) {
            Path abs = Paths.get(dir).toAbsolutePath().normalize();
            if (abs.startsWith(root) && !abs.equals(root)) {
                paths.add(abs);
            }
        }
        Path database = Paths.get(config.storage().database()).toAbsolutePath().normalize();
        for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
            paths.add(Paths.get(database + suffix));
        }
        paths.add(Paths.get(config.storage().dataDirectory(), PENDING_RESTORE_FILE).toAbsolutePath().normalize());
        return paths;
    }

    private static boolean isPanelData(Path path, List<Path> panelData) {
        Path abs = path.toAbsolutePath().normalize();
        for (Path p : panelData) {
            if (abs.startsWith(p)) {
                return true;
            }
        }
        return false;
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
