package br.com.craftpanel.backups;

import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.database.DatabaseManager;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public class BackupServiceTest {

    @Test
    void testBackupSkipsPanelDataAndHostRestoreWaitsForNextStart(@TempDir Path tmp) throws Exception {
        Path serverDir = tmp.resolve("server");
        Path dataDir = serverDir.resolve("data");
        Path backupDir = dataDir.resolve("backups");
        Files.createDirectories(backupDir);
        Files.createDirectories(serverDir.resolve("world/region"));
        Files.createDirectories(serverDir.resolve("plugins"));
        Files.writeString(serverDir.resolve("server.properties"), "motd=original");
        Files.writeString(serverDir.resolve("world/region/r.0.0.mca"), "chunks-v1");
        Files.writeString(serverDir.resolve("plugins/Foo.jar"), "jar-v1");
        Files.writeString(backupDir.resolve("previous.zip"), "older backup");

        PanelConfig config = new PanelConfig(
                new PanelConfig.ServerSettings("127.0.0.1", 2222),
                new PanelConfig.SecuritySettings(false, 60, false, 5, 15),
                new PanelConfig.StorageSettings(dataDir.toString(), dataDir.resolve("database.db").toString(), backupDir.toString(), dataDir.resolve("servers").toString()),
                new PanelConfig.ModrinthSettings(true, "test", "https://api.modrinth.com/v2"),
                new PanelConfig.MonitoringSettings(3, 60),
                new PanelConfig.LoggingSettings("INFO")
        );
        DatabaseManager db = new DatabaseManager(config.storage().database());
        new ServerRepository(db);
        BackupService service = new BackupService(db, config);
        MinecraftServer server = new MinecraftServer("local", "Test", "", "Paper", "26.2", "paper.jar", serverDir.toString(),
                "java", 512, 1024, 25565, "", true, Instant.now(), Instant.now());

        BackupMetadata meta = service.createBackup(server, "tester", "", null);
        List<String> entries;
        try (ZipFile zip = new ZipFile(backupDir.resolve("local").resolve(meta.filename()).toFile())) {
            entries = zip.stream().map(ZipEntry::getName).toList();
        }
        assertTrue(entries.contains("server.properties"), entries.toString());
        assertTrue(entries.contains("world/region/r.0.0.mca"), entries.toString());
        assertTrue(entries.stream().noneMatch(e -> e.startsWith("data/")), entries.toString());

        Files.writeString(serverDir.resolve("server.properties"), "motd=changed");
        Files.writeString(serverDir.resolve("world/region/r.0.0.mca"), "chunks-v2");
        Files.writeString(serverDir.resolve("plugins/Foo.jar"), "jar-v2");

        service.scheduleRestore(server, meta.id(), "tester");
        assertEquals("motd=changed", Files.readString(serverDir.resolve("server.properties")));

        BackupService.applyPendingRestore(config, serverDir);
        assertEquals("motd=original", Files.readString(serverDir.resolve("server.properties")));
        assertEquals("chunks-v1", Files.readString(serverDir.resolve("world/region/r.0.0.mca")));
        assertEquals("jar-v2", Files.readString(serverDir.resolve("plugins/Foo.jar")));
        assertFalse(Files.exists(dataDir.resolve("pending-restore.properties")));
    }
}
