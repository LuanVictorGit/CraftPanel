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
<li><strong>Interactive Real-Time Console:</strong> High-performance WebSocket terminal with ANSI and Minecraft color code parsing, live log streaming, instant search, toggleable autoscroll, and command execution on the server thread with Folia support.</li>
<li><strong>Advanced Syntax-Highlighted File Editor:</strong> Full-featured in-browser code editor featuring synchronized line numbers, search and replace with match counts, Tab key indentation, and syntax highlighting for YAML (<code>config.yml</code>, <code>spigot.yml</code>, <code>paper-global.yml</code>), Properties (<code>server.properties</code>), JSON, and script files.</li>
<li><strong>Plugin Manager &amp; Modrinth Integration:</strong> Browse, enable, disable, and delete installed plugins with automatic backup preservation. Discover, inspect, and install plugins directly from the official Modrinth API v2 with one click.</li>
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
<li>Open your web browser and navigate to:
<pre><code>http://127.0.0.1:8080</code></pre>
</li>
<li>On first startup, the panel will prompt you to create the master administrator account.</li>
<li>Log in and start administering your server!</li>
</ol>

<hr>

<h3>6. Configuration Reference (config.yml)</h3>
<p>CraftPanel Web creates a fully commented <code>config.yml</code> file in its plugin folder:</p>
<pre><code># Language setting ("en" for English, "pt" for Portuguese)
language: "en"

# HTTP network binding
server:
  host: "127.0.0.1"
  port: 8080

# Security policy
security:
  remote_access_enabled: false
  session_timeout_minutes: 60
  require_secure_cookies: false
  max_failed_login_attempts: 5
  lockout_duration_minutes: 15

# Modrinth official API integration
modrinth:
  enabled: true
  user_agent: "CraftPanel-Web/1.2 (contact: lHawk_)"
  api_url: "https://api.modrinth.com/v2"

# System monitoring
monitoring:
  refresh_interval_seconds: 3</code></pre>

<hr>

<h3>7. Performance and Security Architecture</h3>
<ul>
<li><strong>Asynchronous Thread Isolation:</strong> The Javalin web server, WebSocket handlers, and SQLite database transactions run strictly on background worker threads, never blocking server ticks.</li>
<li><strong>Folia-Compatible Scheduling:</strong> Console command dispatching uses Folia's <code>GlobalRegionScheduler</code> when running on Folia, ensuring complete thread safety.</li>
<li><strong>Path Traversal Defense:</strong> All file reading, writing, renaming, and uploading routines canonicalize paths and verify boundaries to prevent unauthorized filesystem access.</li>
<li><strong>Argon2 / BCrypt Password Hashing:</strong> User passwords are encrypted using modern cryptographic salt hashing.</li>
<li><strong>Zero Bloat:</strong> The panel frontend is built with pure vanilla ES6 JavaScript and CSS, requiring no heavy NPM build steps or node dependencies.</li>
</ul>

<hr>

<h3>8. Frequently Asked Questions (FAQ)</h3>
<details>
<summary><strong>How do I allow my friends or staff to access the panel remotely?</strong></summary>
<p>By default, the panel binds to <code>127.0.0.1</code> for security. To allow remote access, set <code>remote_access_enabled: true</code> and change <code>host: "0.0.0.0"</code> in <code>config.yml</code>, then configure your firewall or port forwarding for port 8080.</p>
</details>

<details>
<summary><strong>Can I install plugins from Modrinth without restarting?</strong></summary>
<p>Yes. Installing plugins via the Modrinth store downloads the official JAR directly to your <code>plugins/</code> folder. You can load it using your favorite plugin manager or reload command.</p>
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
