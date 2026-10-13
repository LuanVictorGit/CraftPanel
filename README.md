# CraftPanel Web

CraftPanel Web is a modern, lightweight, secure, and production-ready Minecraft server administration web panel and native Bukkit/Spigot/Paper/Folia plugin built with **Java 21**, **Javalin 6**, **SQLite**, and vanilla **HTML5/CSS3/JavaScript**.

CraftPanel Web is designed to run directly inside the Minecraft server (or standalone), managing the **host server instance** where the plugin is active with zero external daemon dependencies.

## Version

Current version: **1.2.2**

## Version History

- **1.2.2**: Default binding set to `0.0.0.0:2222` with remote access enabled by default. Added console one-time setup security key verification to prevent unauthorized initial admin hijacking. Implemented bidirectional real-time file management and active file editor synchronization via WebSocket. Added in-game command autocomplete and command helper indicator to the console. Fixed text-node search highlighting and match navigation in the file editor. Added user permission checkbox assignment on account creation and fixed checkbox pre-selection on permission edits. Replaced emojis across the entire web panel with clean hand-drawn vector SVGs. Added dynamic online uptime counter alongside server controls.
- **1.2.1**: Added dynamic runtime plugin command registration into `CommandMap` and real-time synchronization with Mojang Brigadier (`CraftServer.syncCommands()`) and player packet updates (`Player.updateCommands()`), enabling dynamically installed plugins to function immediately without server restarts. Implemented `load: STARTUP` so CraftPanel initializes before world loading. Added full ANSI terminal colors and continuous CMD fidelity to the web console with zero loss of startup lines. Implemented reactive server lifecycle buttons (`Start`, `Stop`, `Restart`) with animated transition screen overlay. Added safe plugin unload, disable, and deletion with optional configuration directory cleanup and automatic ZIP backups.
- **1.2.0**: Added advanced syntax-highlighted code editor with synchronized line numbers, search/replace, and color-coded comments/keys/values for YAML, properties, and JSON. Added dedicated `+ Install Plugin` modal in Plugins tab with Modrinth 1-click installer and drag-and-drop local JAR upload. Fixed server restart button with detached background process launcher on Windows and Linux. Fixed real-time console WebSocket streaming and added ANSI/Minecraft color code formatting. Updated MineBuilders `DESCRIPTION.md` to English.
- **1.1.0**: Enforce single-instance host server architecture (managing the host server where the plugin is running). Added native Spigot, Paper, and Folia plugin wrapper (`CraftPanelPlugin`) with `/craftpanel` commands (`status`, `url`, `reload`, `createuser`), live `latest.log` tailing to WebSocket console, thread-safe command execution via Bukkit main thread, language toggle (en/pt), and MineBuilders documentation update.
- **1.0.0**: Initial release with full Javalin web server, SQLite database, RBAC, interactive WebSocket console, secure file manager, Modrinth v2 plugin store, backups, and real-time resource monitoring.

## Supported Platforms & Minecraft Versions

- **Minecraft Versions**: 1.16.5 through 26.2 (latest).
- **Server Platforms**: Spigot, Paper, Purpur, Folia (Folia-supported flag enabled).
- **Java Requirement**: Java 21 LTS or newer.

## Architecture

CraftPanel Web operates in **Single-Instance Host Mode**:
- The web panel directly manages the host server process in which the plugin is installed.
- **Console**: Streams live output from `logs/latest.log` via WebSocket and dispatches commands directly through `Bukkit.dispatchCommand()`.
- **Files**: Browses and edits configurations (`server.properties`, `bukkit.yml`, `spigot.yml`, `paper-global.yml`) securely sandboxed to the server root.
- **Plugins**: Manages installed JAR files in `plugins/` and installs new plugins directly via official Modrinth API.
- **Monitoring**: Live metrics for CPU, JVM memory, disk space, and connected players (`Bukkit.getOnlinePlayers()`).
- **Backups**: Creates ZIP archives of worlds, configurations, and plugins into `data/backups/`.

## In-Game & Console Commands

| Command | Permission | Description |
|---|---|---|
| `/craftpanel status` | `craftpanel.use` | Displays web server status, address, and host details |
| `/craftpanel url` | `craftpanel.use` | Displays the direct browser URL |
| `/craftpanel reload` | `craftpanel.admin` | Reloads `config.yml` and restarts the web service |
| `/craftpanel createuser <user> <pass> [role]` | `craftpanel.admin` | Immediately creates a panel user (`admin`, `moderator`, `viewer`) |

## Permissions

- `craftpanel.admin`: Full administrative access (default: `op`).
- `craftpanel.use`: Access to status and URL commands (default: `op`).

## Building from Source

```bash
mvn clean package
```

The resulting shaded artifact will be generated at:
```bash
target/CraftPanel.jar
```

## Installation & Deployment

1. Copy `target/CraftPanel.jar` into the `plugins/` directory of your Minecraft server.
2. Start or restart the server.
3. Access the web panel at `http://127.0.0.1:8080`.
4. Create the initial administrator account on the setup screen or via console:
   ```bash
   /craftpanel createuser admin my_secure_password admin
   ```

## Configuration (config.yml)

Configuration is located in `plugins/CraftPanel/config.yml`:

```yaml
# Language: "en" (English) or "pt" (Portuguese)
language: "en"

server:
  host: "127.0.0.1"
  port: 8080

security:
  remote_access_enabled: false
  session_timeout_minutes: 60
  require_secure_cookies: false
  max_failed_login_attempts: 5
  lockout_duration_minutes: 15

storage:
  data_directory: "./data"
  database: "./data/database.db"
  backup_directory: "./data/backups"
```

## Author & Contact

- **Author**: `lHawk_`
- **Discord Community**: [https://minebuilders.com.br/discord](https://minebuilders.com.br/discord)

## License

MIT License. See [LICENSE](LICENSE) for details.
