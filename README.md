# CraftPanel Web

CraftPanel Web is a modern, lightweight, secure, and production-ready web administration panel for Minecraft servers, built with **Java 21**, **Javalin**, **SQLite**, and vanilla **HTML5/CSS3/JavaScript**.

## Key Features

- **Process Lifecycle Management**: Real process control using `ProcessBuilder` (Start, Graceful Stop via `stop` command, Force Kill, Restart, Auto-restart detection).
- **Interactive Real-Time Console**: WebSocket-powered live terminal with ANSI color rendering, autoscroll pause, search, and safe command execution (`console.execute` permission required).
- **Secure File Manager**: Full file browser, multi-line editor for Minecraft configurations (`server.properties`, `bukkit.yml`, `spigot.yml`, `paper-global.yml`), upload, download, move, rename, delete, protected with strict canonical path traversal validation.
- **Plugin Management & Official Modrinth Integration**: Automatic detection and metadata extraction of JAR plugins (`plugin.yml` / `paper-plugin.yml`), search, filter by version (1.8 to latest) and loader (Paper, Folia, Purpur, Spigot, Bukkit), 1-click install with checksum verification.
- **Server Installer**: Automated setup wizard supporting PaperMC (Paper, Folia), Purpur, and Vanilla with automatic EULA acceptance and JVM memory configuration.
- **Role-Based Access Control (RBAC)**: Individual accounts, customizable roles, and per-server granular permission assignment.
- **Real-Time Monitoring**: CPU, JVM & host RAM, disk storage, and live player count via Server List Ping handshake.
- **Backups & Recovery**: ZIP archive generation, checksum verification, and assisted restore.
- **Persistent Audit Logging**: Track all administrative and file operations.

## Requirements

- **Java Development Kit (JDK)**: Java 21 or later.
- **Apache Maven**: 3.8+ (for building).

## Building from Source

```bash
mvn clean package
```

The executable standalone fat JAR will be generated at:
```bash
target/craftpanel-web.jar
```

## Running the Application

```bash
java -jar target/craftpanel-web.jar
```

Access the panel in your browser at:
```
http://127.0.0.1:8080
```

On first launch, you will be prompted to create the initial Administrator account.

## Configuration

Configuration is stored in `craftpanel.yml`. Key defaults:

```yaml
server:
  host: "127.0.0.1"
  port: 8080

security:
  remote_access_enabled: false
  session_timeout_minutes: 60
  require_secure_cookies: false

storage:
  data_directory: "./data"
  database: "./data/database.db"
  backup_directory: "./data/backups"
  servers_directory: "./data/servers"
```

## Author

- **Author**: `lHawk_`
- **Discord Community**: [https://minebuilders.com.br/discord](https://minebuilders.com.br/discord)

## License

MIT License. See [LICENSE](LICENSE) for details.
