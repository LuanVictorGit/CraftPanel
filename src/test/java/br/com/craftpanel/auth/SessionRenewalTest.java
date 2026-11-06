package br.com.craftpanel.auth;

import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.database.DatabaseManager;
import br.com.craftpanel.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRenewalTest {
    private AuthService authService;
    private UserRepository userRepo;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        Path db = dir.resolve("test.db");
        userRepo = new UserRepository(new DatabaseManager(db.toString()));
        authService = new AuthService(userRepo, new PanelConfig(
                new PanelConfig.ServerSettings("127.0.0.1", 8080),
                new PanelConfig.SecuritySettings(false, 60, false, 5, 15),
                new PanelConfig.StorageSettings(dir.toString(), db.toString(), dir.toString(), dir.toString()),
                new PanelConfig.ModrinthSettings(true, "test", "https://api.modrinth.com/v2"),
                new PanelConfig.MonitoringSettings(3, 60),
                new PanelConfig.LoggingSettings("INFO")));
        authService.setupFirstAdmin("admin", "Password123!", "Admin");
    }

    @Test
    void activeSessionIsPushedForwardAndPersisted() {
        Session session = authService.login("admin", "Password123!", "1.1.1.1", "test");
        Instant aboutToExpire = Instant.now().plus(Duration.ofMinutes(2));
        userRepo.extendSession(session.token(), aboutToExpire);

        authService.renewSession(authService.validateSession(session.token()).orElseThrow());

        Instant renewed = authService.validateSession(session.token()).orElseThrow().expiresAt();
        assertTrue(renewed.isAfter(Instant.now().plus(Duration.ofMinutes(59))), "expiry should be a full timeout away: " + renewed);
    }

    @Test
    void renewingRepeatedlyWritesAtMostOncePerMinute() {
        Session session = authService.login("admin", "Password123!", "1.1.1.1", "test");
        Instant before = authService.validateSession(session.token()).orElseThrow().expiresAt();

        authService.renewSession(authService.validateSession(session.token()).orElseThrow());

        assertEquals(before, authService.validateSession(session.token()).orElseThrow().expiresAt());
    }
}
