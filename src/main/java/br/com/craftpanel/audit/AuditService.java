package br.com.craftpanel.audit;

import br.com.craftpanel.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);
    private final DatabaseManager db;

    public AuditService(DatabaseManager db) {
        this.db = db;
    }

    public void log(String userId, String username, String action, String targetType, String targetId, String details, String ipAddress, String status) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();

        String sql = "INSERT INTO audit_logs (id, user_id, username, action, target_type, target_id, details, ip_address, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, userId);
            stmt.setString(3, username != null ? username : "System");
            stmt.setString(4, action);
            stmt.setString(5, targetType);
            stmt.setString(6, targetId);
            stmt.setString(7, details);
            stmt.setString(8, ipAddress);
            stmt.setString(9, status);
            stmt.setString(10, now.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to insert audit log", e);
        }
    }

    public List<AuditLog> listLogs(int limit, int offset, String search) {
        List<AuditLog> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, user_id, username, action, target_type, target_id, details, ip_address, status, created_at FROM audit_logs ");
        if (search != null && !search.isBlank()) {
            sql.append("WHERE LOWER(action) LIKE LOWER(?) OR LOWER(username) LIKE LOWER(?) OR LOWER(details) LIKE LOWER(?) ");
        }
        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int p = 1;
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim() + "%";
                stmt.setString(p++, pattern);
                stmt.setString(p++, pattern);
                stmt.setString(p++, pattern);
            }
            stmt.setInt(p++, Math.min(Math.max(limit, 1), 200));
            stmt.setInt(p, Math.max(offset, 0));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new AuditLog(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("target_type"),
                            rs.getString("target_id"),
                            rs.getString("details"),
                            rs.getString("ip_address"),
                            rs.getString("status"),
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing audit logs", e);
        }
        return list;
    }
}
