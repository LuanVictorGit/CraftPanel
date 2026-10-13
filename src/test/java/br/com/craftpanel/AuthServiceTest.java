package br.com.craftpanel;

import br.com.craftpanel.auth.AuthService;
import br.com.craftpanel.auth.PasswordHasher;
import br.com.craftpanel.auth.Session;
import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.database.DatabaseManager;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AuthServiceTest {

    private AuthService authService;
    private UserRepository userRepo;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("test.db");
        DatabaseManager db = new DatabaseManager(dbPath.toString());
        userRepo = new UserRepository(db);

        PanelConfig config = new PanelConfig(
                new PanelConfig.ServerSettings("127.0.0.1", 8080),
                new PanelConfig.SecuritySettings(false, 60, false, 5, 15),
                new PanelConfig.StorageSettings(tempDir.toString(), dbPath.toString(), tempDir.toString(), tempDir.toString()),
                new PanelConfig.ModrinthSettings(true, "CraftPanel-Web/1.0", "https://api.modrinth.com/v2"),
                new PanelConfig.MonitoringSettings(3, 60),
                new PanelConfig.LoggingSettings("INFO")
        );

        authService = new AuthService(userRepo, config);
    }

    @Test
    void testPasswordHasher() {
        String raw = "SecurePassword123!";
        String hash = PasswordHasher.hash(raw);
        assertNotNull(hash);
        assertTrue(PasswordHasher.verify(raw, hash));
        assertFalse(PasswordHasher.verify("wrong", hash));
    }

    @Test
    void testInitialAdminSetup() {
        assertTrue(authService.isSetupRequired());

        User admin = authService.setupFirstAdmin("admin", "AdminPassword123!", "Master Admin");
        assertNotNull(admin);
        assertEquals("admin", admin.username());
        assertEquals("Master Admin", admin.displayName());
        assertEquals("admin", admin.roleId());

        assertFalse(authService.isSetupRequired());

        assertThrows(IllegalStateException.class, () -> {
            authService.setupFirstAdmin("admin2", "AdminPassword123!", "Second Admin");
        });
    }

    @Test
    void testLoginSuccessAndFailure() {
        authService.setupFirstAdmin("admin", "AdminPassword123!", "Admin");

        Session session = authService.login("admin", "AdminPassword123!", "127.0.0.1", "JUnit-Agent");
        assertNotNull(session);
        assertNotNull(session.token());
        assertEquals("admin", session.username());

        assertTrue(authService.validateSession(session.token()).isPresent());

        assertThrows(SecurityException.class, () -> {
            authService.login("admin", "WrongPassword!", "127.0.0.1", "JUnit-Agent");
        });
    }
}
