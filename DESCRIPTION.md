<center>
<h2>CraftPanel Web</h2>
<p><strong>Modern, Lightweight, and Comprehensive Web Administration Panel for Minecraft Servers</strong></p>
<p><em>Developed by lHawk_</em></p>
</center>

<hr>

<h3>1. Overview</h3>
<p><strong>CraftPanel Web</strong> is a native, production-grade Minecraft plugin and integrated web administration panel designed to manage, monitor, and configure your Minecraft server directly from any web browser. Inspired by the clean usability and structure of Pterodactyl Panel, CraftPanel Web operates natively from inside your server without requiring separate daemon processes, external panel infrastructures, or heavy background runtimes.</p>

<p>Installed directly into your server's <code>plugins/</code> folder, CraftPanel Web embeds a high-performance Javalin web server powered by Java 21 and a persistent local SQLite database. It gives server administrators, staff members, and friends full control over console commands, configuration files, plugin management, Modrinth downloads, system health, and backups through a clean, responsive web interface.</p>

<hr>

<h3>2. Key Features</h3>
<ul>
<li><strong>Single Native Instance:</strong> Directly controls the running Minecraft host instance, eliminating the need for complex multi-node daemons or remote agent setups.</li>
<li><strong>Interactive Real-Time Console:</strong> High-performance WebSocket terminal with ANSI and Minecraft color code parsing, CMD-level fidelity, zero-latency log streaming from byte 0, instant search, toggleable autoscroll, and thread-safe command execution with Folia support.</li>
<li><strong>Dynamic Plugin Loading with Brigadier Sync:</strong> Hot-load, enable, and unload plugins at runtime with automatic Bukkit <code>CommandMap</code> registration, Mojang Brigadier tree rebuild (<code>syncCommands</code>), and client packet updates (<code>updateCommands</code>). Installed plugins run their commands immediately without restarting the server.</li>
<li><strong>Advanced Syntax-Highlighted File Editor:</strong> Full-featured in-browser code editor featuring synchronized line numbers, search and replace with match counts, Tab key indentation, and syntax highlighting for YAML (<code>config.yml</code>, <code>spigot.yml</code>, <code>paper-global.yml</code>), Properties (<code>server.properties</code>), JSON, and script files.</li>
<li><strong>Plugin Manager &amp; Modrinth Integration:</strong> Browse, enable, disable, and delete installed plugins with automatic backup preservation and optional configuration folder deletion. Discover, inspect, and install plugins directly from the official Modrinth API v2 with one click.</li>
<li><strong>Reactive Server Lifecycle Management:</strong> Dynamic Start, Stop, and Restart controls that react instantly to server state with an animated transition overlay and automatic process recovery.</li>
<li><strong>Granular Role-Based Access Control (RBAC):</strong> Create and assign user accounts with custom roles (Admin, Server Manager, Operator, Viewer) and fine-grained permissions. Add accounts directly in-game or from console using <code>/craftpanel createuser</code>.</li>
<li><strong>Real-Time Monitoring &amp; Telemetry:</strong> Live metrics tracking CPU load, JVM memory allocation, free disk space, server uptime, and online player count.</li>
<li><strong>Integrated Backup Engine:</strong> Create automated or manual ZIP archives of server files with integrity hashing and instant web download.</li>
<li><strong>Persistent Audit Logging:</strong> Comprehensive record of all logins, file modifications, console commands, and permission changes stored in local SQLite database.</li>
<li><strong>Internationalization (i18n):</strong> English by default with instant runtime language switching to Portuguese.</li>
</ul>

<hr>

<h3>3. Platform Compatibility</h3>
<table>
<tr>
<th>Platform</th>
<th>Supported Versions</th>
<th>Compatibility Status</th>
</tr>
<tr>
<td><strong>Paper</strong></td>
<td>1.16.5 to 26.2+</td>
<td>Fully Compatible</td>
</tr>
<tr>
<td><strong>Spigot</strong></td>
<td>1.16.5 to 26.2+</td>
<td>Fully Compatible</td>
</tr>
<tr>
<td><strong>Purpur</strong></td>
<td>1.16.5 to 26.2+</td>
<td>Fully Compatible</td>
</tr>
<tr>
<td><strong>Folia</strong></td>
<td>1.19.4 to 26.2+</td>
<td>Fully Compatible (Region Schedulers)</td>
</tr>
</table>

<hr>

<h3>4. Commands and Permissions</h3>
<table>
<tr>
<th>Command</th>
<th>Permission</th>
<th>Description</th>
</tr>
<tr>
<td><code>/craftpanel status</code></td>
<td><code>craftpanel.use</code></td>
<td>Displays the status of the web server, HTTP address, and running host information.</td>
</tr>
<tr>
<td><code>/craftpanel url</code></td>
<td><code>craftpanel.use</code></td>
<td>Shows the direct web address to access the administration panel.</td>
</tr>
<tr>
<td><code>/craftpanel reload</code></td>
<td><code>craftpanel.admin</code></td>
<td>Reloads <code>config.yml</code> and restarts the embedded HTTP server.</td>
</tr>
<tr>
<td><code>/craftpanel createuser &lt;username&gt; &lt;password&gt; [role]</code></td>
<td><code>craftpanel.admin</code></td>
<td>Instantly creates a new web panel account with the specified role (admin, moderator, viewer).</td>
</tr>
</table>

<hr>

<h3>5. Step-by-Step Installation</h3>
<ol>
<li>Download the latest <code>CraftPanel.jar</code> file.</li>
<li>Place the jar into your server's <code>plugins/</code> folder.</li>
<li>Start or restart your Minecraft server.</li>
<li>Notice the console message containing your one-time <strong>Setup Security Key</strong> (e.g. <code>CP-ABCD-1234</code>).</li>
<li>Open your web browser and navigate to:
<pre><code>http://localhost:2222</code></pre>
</li>
<li>Enter the console security key along with your administrator username and password to create the master account.</li>
<li>Log in and start administering your server!</li>
</ol>

<hr>

<h3>6. Configuration Reference (config.yml)</h3>
<p>CraftPanel Web creates a fully commented <code>config.yml</code> file in its plugin folder:</p>
<pre><code># Language setting ("en" for English, "pt" for Portuguese)
language: "en"

# HTTP network binding (default 0.0.0.0:2222 for immediate remote access)
server:
  host: "0.0.0.0"
  port: 2222

# Security policy
security:
  remote_access_enabled: true
  session_timeout_minutes: 60
  require_secure_cookies: false
  max_failed_login_attempts: 5
  lockout_duration_minutes: 15

# Modrinth official API integration
modrinth:
  enabled: true
  user_agent: "CraftPanel-Web/1.2.2 (contact: minebuilders.com.br/discord)"
  api_url: "https://api.modrinth.com/v2"

# System monitoring
monitoring:
  refresh_interval_seconds: 3</code></pre>

<hr>

<h3>7. Performance and Security Architecture</h3>
<ul>
<li><strong>Console One-Time Security Key:</strong> On first boot, CraftPanel generates a secure temporary key announced directly to the server console, preventing unauthorized players from claiming the administrator account before you do.</li>
<li><strong>Bidirectional Real-Time Synchronization:</strong> File modifications and directory changes synchronize instantly across all connected panel users via WebSocket, including open code editor tabs.</li>
<li><strong>In-Console Command Autocomplete:</strong> Native command suggestion bar and autocomplete matching in-game behavior for effortless command execution.</li>
<li><strong>Asynchronous Thread Isolation:</strong> The Javalin web server, WebSocket handlers, and SQLite database transactions run strictly on background worker threads, never blocking server ticks.</li>
<li><strong>Folia-Compatible Scheduling:</strong> Console command dispatching uses Folia's <code>GlobalRegionScheduler</code> when running on Folia, ensuring complete thread safety.</li>
<li><strong>Path Traversal Defense:</strong> All file reading, writing, renaming, and uploading routines canonicalize paths and verify boundaries to prevent unauthorized filesystem access.</li>
<li><strong>Argon2 / BCrypt Password Hashing:</strong> User passwords are encrypted using modern cryptographic salt hashing.</li>
<li><strong>Zero Bloat &amp; Hand-Drawn Vector Icons:</strong> Built with pure vanilla ES6 JavaScript and clean vector SVGs, with zero emojis and zero external frontend dependencies.</li>
</ul>

<hr>

<h3>8. Frequently Asked Questions (FAQ)</h3>
<details>
<summary><strong>How do I access the panel remotely?</strong></summary>
<p>By default, CraftPanel binds to <code>0.0.0.0</code> on port <code>2222</code> with remote access enabled. You can connect from anywhere by typing your server's public IP and port 2222 into your web browser (make sure port 2222 is forwarded in your router or firewall).</p>
</details>

<details>
<summary><strong>What is the Console Security Key?</strong></summary>
<p>When the plugin starts for the first time without an admin account configured, it announces a one-time security key in your server console. This key must be entered on the initial web setup page to verify that you are the legitimate server owner.</p>
</details>

<details>
<summary><strong>Can I install plugins from Modrinth without restarting?</strong></summary>
<p>Yes. When installing plugins via Modrinth or uploading local JAR files through the web panel, CraftPanel automatically hot-loads the plugin, registers its commands into Bukkit and Mojang Brigadier, and updates client command packets in real time. Commands become accessible immediately without restarting the server.</p>
</details>

<details>
<summary><strong>Where is panel data stored?</strong></summary>
<p>All user accounts, sessions, roles, audit logs, and settings are stored locally in <code>plugins/CraftPanel/database.db</code> via SQLite.</p>
</details>

<hr>

<center>
<p><strong>Need help or support?</strong></p>
<p>Author: <strong>lHawk_</strong> | Community Discord: <a href="https://minebuilders.com.br/discord">https://minebuilders.com.br/discord</a></p>
</center>
