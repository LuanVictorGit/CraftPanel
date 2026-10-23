package br.com.craftpanel.users;

import br.com.craftpanel.auth.Session;
import br.com.craftpanel.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class UserRepository {
    private final DatabaseManager db;

    public UserRepository(DatabaseManager db) {
        this.db = db;
    }

    public boolean hasAnyUsers() {
        String sql = "SELECT 1 FROM users LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Error checking users count", e);
        }
    }

    public Optional<User> findById(String id) {
        String sql = "SELECT id, username, display_name, role_id, created_at, updated_at FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding user by id", e);
        }
        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, display_name, role_id, created_at, updated_at FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding user by username", e);
        }
        return Optional.empty();
    }

    public Optional<String> getPasswordHash(String userId) {
        String sql = "SELECT password_hash FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("password_hash"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching password hash", e);
        }
        return Optional.empty();
    }

    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, username, display_name, role_id, created_at, updated_at FROM users ORDER BY created_at ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing users", e);
        }
        return list;
    }

    public void create(User user, String passwordHash) {
        String sql = "INSERT INTO users (id, username, password_hash, display_name, role_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.id());
            stmt.setString(2, user.username());
            stmt.setString(3, passwordHash);
            stmt.setString(4, user.displayName());
            stmt.setString(5, user.roleId());
            stmt.setString(6, user.createdAt().toString());
            stmt.setString(7, user.updatedAt().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating user", e);
        }
    }

    public void update(User user) {
        String sql = "UPDATE users SET display_name = ?, role_id = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.displayName());
            stmt.setString(2, user.roleId());
            stmt.setString(3, Instant.now().toString());
            stmt.setString(4, user.id());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating user", e);
        }
    }

    public void updatePassword(String userId, String newHash) {
        String sql = "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newHash);
            stmt.setString(2, Instant.now().toString());
            stmt.setString(3, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating password", e);
        }
    }

    public void delete(String userId) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting user", e);
        }
    }

    public Optional<Role> findRoleById(String roleId) {
        String sql = "SELECT id, name, description, is_system FROM roles WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, roleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    List<String> perms = getRolePermissions(conn, roleId);
                    return Optional.of(new Role(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getInt("is_system") == 1,
                            perms
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding role", e);
        }
        return Optional.empty();
    }

    public List<Role> findAllRoles() {
        List<Role> list = new ArrayList<>();
        String sql = "SELECT id, name, description, is_system FROM roles ORDER BY is_system DESC, name ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                List<String> perms = getRolePermissions(conn, id);
                list.add(new Role(
                        id,
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("is_system") == 1,
                        perms
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing roles", e);
        }
        return list;
    }

    public void createRole(Role role) {
        String sql = "INSERT INTO roles (id, name, description, is_system) VALUES (?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, role.id());
            stmt.setString(2, role.name());
            stmt.setString(3, role.description());
            stmt.setInt(4, role.isSystem() ? 1 : 0);
            stmt.executeUpdate();

            setRolePermissions(conn, role.id(), role.permissions());
        } catch (SQLException e) {
            throw new RuntimeException("Error creating role", e);
        }
    }

    public void updateRole(Role role) {
        String sql = "UPDATE roles SET name = ?, description = ? WHERE id = ? AND is_system = 0";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, role.name());
            stmt.setString(2, role.description());
            stmt.setString(3, role.id());
            stmt.executeUpdate();

            setRolePermissions(conn, role.id(), role.permissions());
        } catch (SQLException e) {
            throw new RuntimeException("Error updating role", e);
        }
    }

    public void deleteRole(String roleId) {
        String sql = "DELETE FROM roles WHERE id = ? AND is_system = 0";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, roleId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting role", e);
        }
    }

    private List<String> getRolePermissions(Connection conn, String roleId) throws SQLException {
        List<String> perms = new ArrayList<>();
        String sql = "SELECT permission FROM role_permissions WHERE role_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, roleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    perms.add(rs.getString("permission"));
                }
            }
        }
        return perms;
    }

    private void setRolePermissions(Connection conn, String roleId, List<String> perms) throws SQLException {
        String del = "DELETE FROM role_permissions WHERE role_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(del)) {
            stmt.setString(1, roleId);
            stmt.executeUpdate();
        }

        String ins = "INSERT INTO role_permissions (role_id, permission) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(ins)) {
            for (String p : perms) {
                stmt.setString(1, roleId);
                stmt.setString(2, p);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public Set<String> getUserServerPermissions(String userId, String serverId) {
        Set<String> set = new HashSet<>();
        String sql = "SELECT permission FROM user_server_permissions WHERE user_id = ? AND server_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, serverId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString("permission"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error getting server permissions", e);
        }
        return set;
    }

    public void setUserServerPermissions(String userId, String serverId, List<String> perms) {
        try (Connection conn = db.getConnection()) {
            String del = "DELETE FROM user_server_permissions WHERE user_id = ? AND server_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(del)) {
                stmt.setString(1, userId);
                stmt.setString(2, serverId);
                stmt.executeUpdate();
            }

            String ins = "INSERT INTO user_server_permissions (user_id, server_id, permission) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(ins)) {
                for (String p : perms) {
                    stmt.setString(1, userId);
                    stmt.setString(2, serverId);
                    stmt.setString(3, p);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error setting user server permissions", e);
        }
    }

    public void saveSession(Session session) {
        String sql = "INSERT INTO sessions (token, user_id, ip_address, user_agent, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, session.token());
            stmt.setString(2, session.userId());
            stmt.setString(3, session.ipAddress());
            stmt.setString(4, session.userAgent());
            stmt.setString(5, session.expiresAt().toString());
            stmt.setString(6, session.createdAt().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving session", e);
        }
    }

    public Optional<Session> findSessionByToken(String token) {
        String sql = """
            SELECT s.token, s.user_id, u.username, u.display_name, u.role_id, s.ip_address, s.user_agent, s.expires_at, s.created_at
            FROM sessions s
            JOIN users u ON s.user_id = u.id
            WHERE s.token = ?
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Instant expiresAt = Instant.parse(rs.getString("expires_at"));
                    if (Instant.now().isAfter(expiresAt)) {
                        deleteSession(token);
                        return Optional.empty();
                    }
                    return Optional.of(new Session(
                            rs.getString("token"),
                            rs.getString("user_id"),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            rs.getString("role_id"),
                            rs.getString("ip_address"),
                            rs.getString("user_agent"),
                            expiresAt,
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding session", e);
        }
        return Optional.empty();
    }

    public void deleteSession(String token) {
        String sql = "DELETE FROM sessions WHERE token = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting session", e);
        }
    }

    /** Deletes every session of the user except {@code keepToken} (pass null to delete them all). */
    public void deleteSessionsForUser(String userId, String keepToken) {
        String sql = keepToken == null
                ? "DELETE FROM sessions WHERE user_id = ?"
                : "DELETE FROM sessions WHERE user_id = ? AND token <> ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            if (keepToken != null) {
                stmt.setString(2, keepToken);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error revoking user sessions", e);
        }
    }

    public void deleteExpiredSessions() {
        String sql = "DELETE FROM sessions WHERE expires_at < ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, Instant.now().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error cleaning expired sessions", e);
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("role_id"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }
}
