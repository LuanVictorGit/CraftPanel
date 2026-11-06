package br.com.craftpanel.mcp;

import br.com.craftpanel.auth.PasswordHasher;
import br.com.craftpanel.database.DatabaseManager;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpTokenServiceTest {
    private static final String USER = "user-1";

    private DatabaseManager db;
    private McpTokenService tokens;

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        db = new DatabaseManager(dir.resolve("test.db").toString());
        tokens = new McpTokenService(db);
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO users (id, username, password_hash, display_name, role_id, created_at, updated_at) VALUES ('"
                    + USER + "', 'a', 'x', 'A', 'admin', '" + Instant.now() + "', '" + Instant.now() + "')");
        }
    }

    @Test
    void createdTokenAuthenticatesAndKeepsItsAccessLevel() {
        McpTokenService.CreatedToken created = tokens.create(USER, "client", true);

        McpTokenService.TokenInfo info = tokens.authenticate(created.token()).orElseThrow();

        assertEquals(USER, info.userId());
        assertTrue(info.readOnly());
    }

    @Test
    void tokenItselfIsNeverStored() throws Exception {
        McpTokenService.CreatedToken created = tokens.create(USER, "client", false);

        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT token_hash FROM mcp_tokens")) {
            assertTrue(rs.next());
            assertFalse(rs.getString(1).contains(created.token().substring(6)));
        }
    }

    @Test
    void unknownAndMalformedTokensAreRejected() {
        assertTrue(tokens.authenticate("cpmcp_0000").isEmpty());
        assertTrue(tokens.authenticate("anything").isEmpty());
        assertTrue(tokens.authenticate(null).isEmpty());
    }

    @Test
    void onlyTheOwnerCanRevokeAndRevokedTokensStopWorking() {
        McpTokenService.CreatedToken created = tokens.create(USER, "client", false);

        assertFalse(tokens.revoke("someone-else", created.info().id()));
        assertTrue(tokens.authenticate(created.token()).isPresent());

        assertTrue(tokens.revoke(USER, created.info().id()));
        assertTrue(tokens.authenticate(created.token()).isEmpty());
    }
}
