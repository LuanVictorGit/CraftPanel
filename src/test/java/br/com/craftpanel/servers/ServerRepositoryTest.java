package br.com.craftpanel.servers;

import br.com.craftpanel.database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.*;

public class ServerRepositoryTest {

    @Test
    void testStaleServerDirIsRefreshedOnStartup(@TempDir Path tempDir) throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("database.db").toString());
        new ServerRepository(db);

        // Simulates a server folder (with its database) copied from another machine.
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE servers SET server_dir = ? WHERE id = ?")) {
            stmt.setString(1, "C:\\Users\\Someone Else\\Desktop\\servidor\\.");
            stmt.setString(2, ServerRepository.HOST_SERVER_ID);
            stmt.executeUpdate();
        }

        ServerRepository restarted = new ServerRepository(db);
        MinecraftServer server = restarted.findById(ServerRepository.HOST_SERVER_ID).orElseThrow();
        assertEquals(Paths.get("").toAbsolutePath().normalize().toString(), server.serverDir());
    }

    @Test
    void testMinecraftVersionPrefersMcTag() {
        assertEquals("26.2", ServerRepository.detectMinecraftVersion(Object.class, "26.2.build.134-alpha (MC: 26.2)", "26.2.build.134-alpha"));
    }

    @Test
    void testMinecraftVersionStripsBuildSuffix() {
        assertEquals("26.2", ServerRepository.detectMinecraftVersion(Object.class, null, "26.2.build.134-alpha"));
        assertEquals("1.21.4", ServerRepository.detectMinecraftVersion(Object.class, "git-Spigot-abc", "1.21.4-R0.1-SNAPSHOT"));
    }
}
