package br.com.craftpanel.mcp;

import br.com.craftpanel.database.DatabaseManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Personal access tokens for MCP clients. Only a hash is stored; the token itself is shown once, when created. */
public class McpTokenService {
    private static final String PREFIX = "cpmcp_";
    private static final Duration LAST_USED_PRECISION = Duration.ofMinutes(1);

    public record TokenInfo(String id, String userId, String name, boolean readOnly, Instant createdAt, Instant lastUsedAt) {}

    public record CreatedToken(TokenInfo info, String token) {}

    private final DatabaseManager db;
    private final SecureRandom random = new SecureRandom();

    public McpTokenService(DatabaseManager db) {
        this.db = db;
    }

    public CreatedToken create(String userId, String name, boolean readOnly) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = PREFIX + HexFormat.of().formatHex(bytes);
        TokenInfo info = new TokenInfo(UUID.randomUUID().toString(), userId, name, readOnly, Instant.now(), null);

        String sql = "INSERT INTO mcp_tokens (id, user_id, name, token_hash, read_only, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, info.id());
            stmt.setString(2, userId);
            stmt.setString(3, name);
            stmt.setString(4, hash(token));
            stmt.setInt(5, readOnly ? 1 : 0);
            stmt.setString(6, info.createdAt().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating MCP token", e);
        }
        return new CreatedToken(info, token);
    }

    public List<TokenInfo> list(String userId) {
        String sql = "SELECT id, user_id, name, read_only, created_at, last_used_at FROM mcp_tokens WHERE user_id = ? ORDER BY created_at DESC";
        List<TokenInfo> tokens = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tokens.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing MCP tokens", e);
        }
        return tokens;
    }

    /** @return whether a token of this user was deleted */
    public boolean revoke(String userId, String tokenId) {
        String sql = "DELETE FROM mcp_tokens WHERE id = ? AND user_id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tokenId);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error revoking MCP token", e);
        }
    }

    public Optional<TokenInfo> authenticate(String token) {
        if (token == null || !token.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String sql = "SELECT id, user_id, name, read_only, created_at, last_used_at FROM mcp_tokens WHERE token_hash = ?";
        try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hash(token));
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                TokenInfo info = map(rs);
                touch(conn, info);
                return Optional.of(info);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking MCP token", e);
        }
    }

    private void touch(Connection conn, TokenInfo info) throws SQLException {
        Instant now = Instant.now();
        if (info.lastUsedAt() != null && info.lastUsedAt().plus(LAST_USED_PRECISION).isAfter(now)) {
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE mcp_tokens SET last_used_at = ? WHERE id = ?")) {
            stmt.setString(1, now.toString());
            stmt.setString(2, info.id());
            stmt.executeUpdate();
        }
    }

    private static TokenInfo map(ResultSet rs) throws SQLException {
        String lastUsed = rs.getString("last_used_at");
        return new TokenInfo(rs.getString("id"), rs.getString("user_id"), rs.getString("name"), rs.getInt("read_only") == 1,
                Instant.parse(rs.getString("created_at")), lastUsed == null ? null : Instant.parse(lastUsed));
    }

    // These tokens carry 256 random bits, so a plain fast hash is enough: there is nothing to brute-force.
    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
