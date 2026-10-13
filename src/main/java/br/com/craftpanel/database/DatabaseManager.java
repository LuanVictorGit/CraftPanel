package br.com.craftpanel.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);
    private final SQLiteDataSource dataSource;

    public DatabaseManager(String dbPath) {
        File dbFile = new File(dbPath);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        this.dataSource = new SQLiteDataSource();
        this.dataSource.setUrl("jdbc:sqlite:" + dbPath);
        initDatabase();
    }

    public Connection getConnection() throws SQLException {
        Connection conn = dataSource.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
        return conn;
    }

    private void initDatabase() {
        try (Connection conn = getConnection()) {
            applySchema(conn);
            seedDefaultRoles(conn);
        } catch (SQLException e) {
            LOGGER.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void applySchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at TEXT NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS roles (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    description TEXT,
                    is_system INTEGER NOT NULL DEFAULT 0
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS role_permissions (
                    role_id TEXT NOT NULL,
                    permission TEXT NOT NULL,
                    PRIMARY KEY (role_id, permission),
                    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    role_id TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    FOREIGN KEY (role_id) REFERENCES roles(id)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    token TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    ip_address TEXT,
                    user_agent TEXT,
                    expires_at TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS servers (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    software TEXT NOT NULL,
                    minecraft_version TEXT NOT NULL,
                    jar_path TEXT NOT NULL,
                    server_dir TEXT NOT NULL,
                    java_executable TEXT NOT NULL,
                    min_memory_mb INTEGER NOT NULL DEFAULT 1024,
                    max_memory_mb INTEGER NOT NULL DEFAULT 2048,
                    server_port INTEGER NOT NULL DEFAULT 25565,
                    jvm_args TEXT,
                    auto_start INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_server_permissions (
                    user_id TEXT NOT NULL,
                    server_id TEXT NOT NULL,
                    permission TEXT NOT NULL,
                    PRIMARY KEY (user_id, server_id, permission),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS backups (
                    id TEXT PRIMARY KEY,
                    server_id TEXT NOT NULL,
                    filename TEXT NOT NULL,
                    file_size_bytes INTEGER NOT NULL,
                    checksum TEXT NOT NULL,
                    created_by TEXT NOT NULL,
                    notes TEXT,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (server_id) REFERENCES servers(id) ON DELETE CASCADE
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id TEXT PRIMARY KEY,
                    user_id TEXT,
                    username TEXT NOT NULL,
                    action TEXT NOT NULL,
                    target_type TEXT,
                    target_id TEXT,
                    details TEXT,
                    ip_address TEXT,
                    status TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    target_id TEXT,
                    state TEXT NOT NULL,
                    progress INTEGER NOT NULL DEFAULT 0,
                    message TEXT,
                    error TEXT,
                    created_at TEXT NOT NULL,
                    completed_at TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
            """);
        }
    }

    private void seedDefaultRoles(Connection conn) throws SQLException {
        insertRoleIfNotExists(conn, "admin", "Administrador", "Acesso irrestrito a todas as funções do painel", 1,
                List.of("*"));

        insertRoleIfNotExists(conn, "server_manager", "Gerente de Servidor", "Gerenciamento completo de instâncias de servidores", 1,
                List.of(
                        "server.view", "server.start", "server.stop", "server.restart", "server.kill",
                        "console.view", "console.execute",
                        "files.view", "files.edit", "files.upload", "files.download", "files.delete",
                        "plugins.view", "plugins.manage",
                        "modrinth.view", "modrinth.install",
                        "backups.view", "backups.manage",
                        "server.edit", "server.create"
                ));

        insertRoleIfNotExists(conn, "operator", "Operador", "Controle de execução e console", 1,
                List.of(
                        "server.view", "server.start", "server.stop", "server.restart",
                        "console.view", "console.execute",
                        "files.view", "plugins.view", "monitoring.view"
                ));

        insertRoleIfNotExists(conn, "viewer", "Visualizador", "Acesso de somente leitura", 1,
                List.of(
                        "server.view", "console.view", "files.view", "plugins.view", "backups.view", "monitoring.view"
                ));
    }

    private void insertRoleIfNotExists(Connection conn, String id, String name, String desc, int isSystem, List<String> perms) throws SQLException {
        String checkSql = "SELECT 1 FROM roles WHERE id = ?";
        try (PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setString(1, id);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }

        String insertRole = "INSERT INTO roles (id, name, description, is_system) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertRole)) {
            stmt.setString(1, id);
            stmt.setString(2, name);
            stmt.setString(3, desc);
            stmt.setInt(4, isSystem);
            stmt.executeUpdate();
        }

        String insertPerm = "INSERT OR IGNORE INTO role_permissions (role_id, permission) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertPerm)) {
            for (String perm : perms) {
                stmt.setString(1, id);
                stmt.setString(2, perm);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}
