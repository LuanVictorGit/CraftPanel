package br.com.craftpanel.tools;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.backups.BackupMetadata;
import br.com.craftpanel.backups.BackupService;
import br.com.craftpanel.files.FileManager;
import br.com.craftpanel.monitoring.MonitoringService;
import br.com.craftpanel.plugins.PluginManager;
import br.com.craftpanel.processes.ConsoleLine;
import br.com.craftpanel.processes.ProcessManager;
import br.com.craftpanel.processes.ProcessState;
import br.com.craftpanel.processes.ServerProcess;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.tasks.Task;
import br.com.craftpanel.tasks.TaskManager;
import br.com.craftpanel.tools.ServerTool.Access;
import br.com.craftpanel.tools.ServerTool.Args;
import br.com.craftpanel.tools.ServerTool.Caller;
import br.com.craftpanel.tools.ServerTool.Handler;
import br.com.craftpanel.tools.ServerTool.Outcome;
import br.com.craftpanel.tools.ServerTool.Param;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Everything an MCP client or the AI chat can do on the server. Each tool checks the caller's own panel permissions, so
 * neither entry point can do more than that user could do in the panel, and every change lands in the audit log.
 */
public class ServerToolbox {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerToolbox.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[a-zA-Z]");
    private static final int MAX_TEXT_CHARS = 60_000;
    private static final Set<String> CONTENT_ARGUMENTS = Set.of("content", "old_text", "new_text");
    private static final Param SERVER = Param.optionalString("server", "Server id. Optional: defaults to the host server.");

    private final ServerService serverService;
    private final ProcessManager processManager;
    private final FileManager fileManager;
    private final PluginManager pluginManager;
    private final MonitoringService monitoringService;
    private final BackupService backupService;
    private final TaskManager taskManager;
    private final UserService userService;
    private final AuditService auditService;
    private final Map<String, ServerTool> tools = new LinkedHashMap<>();

    public ServerToolbox(ServerService serverService, ProcessManager processManager, FileManager fileManager,
                         PluginManager pluginManager, MonitoringService monitoringService, BackupService backupService,
                         TaskManager taskManager, UserService userService, AuditService auditService) {
        this.serverService = serverService;
        this.processManager = processManager;
        this.fileManager = fileManager;
        this.pluginManager = pluginManager;
        this.monitoringService = monitoringService;
        this.backupService = backupService;
        this.taskManager = taskManager;
        this.userService = userService;
        this.auditService = auditService;
        registerServerTools();
        registerConsoleTools();
        registerFileTools();
        registerPluginTools();
        registerBackupTools();
    }

    public List<ServerTool> tools() {
        return List.copyOf(tools.values());
    }

    public Optional<ServerTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Outcome call(String name, Map<String, Object> arguments, Caller caller) {
        ServerTool tool = tools.get(name);
        if (tool == null) {
            return new Outcome("Unknown tool: " + name, true);
        }
        boolean audited = tool.access() != Access.READ;
        try {
            String text = limit(tool.handler().call(new Args(arguments), caller));
            if (audited) {
                audit(tool, arguments, caller, "SUCCESS");
            }
            return new Outcome(text, false);
        } catch (SecurityException | IllegalArgumentException | IllegalStateException expected) {
            if (audited) {
                audit(tool, arguments, caller, "FAILURE");
            }
            return new Outcome(expected.getMessage(), true);
        } catch (Exception e) {
            LOGGER.warn("Tool {} failed", name, e);
            if (audited) {
                audit(tool, arguments, caller, "FAILURE");
            }
            return new Outcome(name + " failed: " + e.getMessage(), true);
        }
    }

    private void audit(ServerTool tool, Map<String, Object> arguments, Caller caller, String status) {
        Map<String, Object> shown = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        // File contents can hold passwords and API keys, and audit.view is a much narrower right than files.view.
        shown.replaceAll((key, value) -> value instanceof String s && CONTENT_ARGUMENTS.contains(key) ? "(" + s.length() + " characters)"
                : value instanceof String s && s.length() > 200 ? s.substring(0, 200) + "..." : value);
        auditService.log(caller.user().id(), caller.user().username(), "TOOL_" + tool.name().toUpperCase(Locale.ROOT), caller.source(),
                tool.name(), "Via " + caller.source() + ": " + shown, caller.ip(), status);
    }

    private void add(String name, String description, Access access, List<Param> params, Handler handler) {
        tools.put(name, new ServerTool(name, description, access, params, handler));
    }

    private static List<Param> params(Param... params) {
        List<Param> all = new ArrayList<>(List.of(params));
        all.add(SERVER);
        return List.copyOf(all);
    }

    private MinecraftServer server(Args args, Caller caller, String permission) {
        String requested = args.optional("server");
        MinecraftServer server;
        if (requested == null || requested.isBlank()) {
            server = serverService.listServers().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No server is configured"));
        } else {
            server = serverService.getServer(requested)
                    .orElseThrow(() -> new IllegalArgumentException("Server not found: " + requested));
        }
        if (!userService.hasServerPermission(caller.user(), server.id(), permission)) {
            throw new SecurityException("Permission denied: " + permission + " on server " + server.name());
        }
        return server;
    }

    private static String json(Object value) throws Exception {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private static String limit(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_TEXT_CHARS ? text
                : text.substring(0, MAX_TEXT_CHARS) + "\n... [truncated, " + (text.length() - MAX_TEXT_CHARS) + " more characters]";
    }

    private void registerServerTools() {
        add("list_servers", "List the Minecraft servers this user can see, with status, version and player count.", Access.READ, List.of(), (args, caller) -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (MinecraftServer server : serverService.listServers()) {
                if (userService.hasServerPermission(caller.user(), server.id(), Permission.SERVER_VIEW)) {
                    result.add(status(server));
                }
            }
            return json(result);
        });

        add("get_server_status", "Status, uptime, CPU, memory and player count of a server.", Access.READ, params(),
                (args, caller) -> json(status(server(args, caller, Permission.SERVER_VIEW))));

        add("get_performance", "TPS, MSPT and per-plugin performance telemetry of a server.", Access.READ, params(),
                (args, caller) -> json(monitoringService.collectSparkTelemetry(server(args, caller, Permission.MONITORING_VIEW))));

        add("get_system_metrics", "CPU, memory and disk of the machine hosting the panel.", Access.READ, List.of(), (args, caller) -> {
            if (!userService.hasPermission(caller.user(), Permission.MONITORING_VIEW)) {
                throw new SecurityException("Permission denied: " + Permission.MONITORING_VIEW);
            }
            return json(monitoringService.collectSystemMetrics());
        });

        add("restart_server", "Restart the Minecraft server. Disconnects every player; the panel goes down with it and comes back.", Access.DESTRUCTIVE, params(),
                (args, caller) -> lifecycle(server(args, caller, Permission.SERVER_RESTART), "restart"));

        add("stop_server", "Stop the Minecraft server gracefully. Disconnects every player; when the panel runs inside this server it stops too and someone must start the server by hand.", Access.DESTRUCTIVE, params(),
                (args, caller) -> lifecycle(server(args, caller, Permission.SERVER_STOP), "stop"));
    }

    private Map<String, Object> status(MinecraftServer server) {
        ServerProcess process = processManager.getProcess(server);
        var metrics = monitoringService.collectMetrics(server);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", server.id());
        map.put("name", server.name());
        map.put("software", server.software());
        map.put("minecraftVersion", server.minecraftVersion());
        map.put("port", server.serverPort());
        map.put("status", process != null ? process.getState().name() : ProcessState.RUNNING.name());
        map.put("uptimeSeconds", process != null ? process.getUptime().toSeconds() : 0);
        map.put("metrics", metrics);
        return map;
    }

    // Runs after a moment so the answer still reaches the client before the panel goes down with the server.
    private String lifecycle(MinecraftServer server, String action) {
        ServerProcess process = requireProcess(server);
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1500);
                if ("restart".equals(action)) {
                    process.restart(25);
                } else {
                    process.stop(25);
                }
            } catch (Exception e) {
                LOGGER.error("Could not {} server {}", action, server.name(), e);
            }
        }, "craftpanel-tool-" + action);
        thread.setDaemon(true);
        thread.start();
        return "Server " + action + " requested for " + server.name() + ". The panel may be unavailable until it is back.";
    }

    private ServerProcess requireProcess(MinecraftServer server) {
        ServerProcess process = processManager.getProcess(server);
        if (process == null) {
            throw new IllegalStateException("Server process unavailable: " + server.name());
        }
        return process;
    }

    private void registerConsoleTools() {
        add("read_console", "Read the latest lines of the server console, optionally only those containing some text.", Access.READ,
                params(Param.optionalInt("lines", "How many lines to return, newest last. Default 100, maximum 1000."),
                        Param.optionalString("contains", "Only return lines containing this text (case-insensitive).")),
                (args, caller) -> {
                    MinecraftServer server = server(args, caller, Permission.CONSOLE_VIEW);
                    int count = args.intValue("lines", 100, 1, 1000);
                    String contains = args.optional("contains");
                    String needle = contains == null ? null : contains.toLowerCase(Locale.ROOT);
                    List<String> lines = new ArrayList<>();
                    for (ConsoleLine line : requireProcess(server).getBuffer().getLines()) {
                        String clean = ANSI.matcher(line.line()).replaceAll("");
                        if (needle == null || clean.toLowerCase(Locale.ROOT).contains(needle)) {
                            lines.add(clean);
                        }
                    }
                    return String.join("\n", lines.subList(Math.max(0, lines.size() - count), lines.size()));
                });

        add("run_command", "Run a command on the server console, with full console privileges, and return the console output it produced.", Access.DESTRUCTIVE,
                params(Param.string("command", "The command, without a leading slash, for example: list"),
                        Param.optionalInt("wait_ms", "How long to collect output, in milliseconds. Default 1500, maximum 15000.")),
                (args, caller) -> {
                    MinecraftServer server = server(args, caller, Permission.CONSOLE_EXECUTE);
                    ServerProcess process = requireProcess(server);
                    String command = args.required("command").trim();
                    List<String> output = Collections.synchronizedList(new ArrayList<>());
                    ServerProcess.Listener listener = new ServerProcess.Listener() {
                        @Override
                        public void onLine(ConsoleLine line) {
                            output.add(ANSI.matcher(line.line()).replaceAll(""));
                        }

                        @Override
                        public void onStateChanged(ProcessState newState) {}
                    };
                    process.addListener(listener);
                    try {
                        process.sendCommand(command);
                        Thread.sleep(args.intValue("wait_ms", 1500, 100, 15_000));
                    } finally {
                        process.removeListener(listener);
                    }
                    synchronized (output) {
                        return String.join("\n", output);
                    }
                });
    }

    private void registerFileTools() {
        add("list_files", "List a folder of the server, relative to its root. CraftPanel's own data is not accessible.", Access.READ,
                params(Param.optionalString("path", "Folder relative to the server root. Default: the root.")),
                (args, caller) -> json(fileManager.listFiles(server(args, caller, Permission.FILES_VIEW), args.optional("path"))));

        add("read_file", "Read a text file of the server (up to 5 MB), optionally a range of lines. Plugin configs live in plugins/<name>/, logs in logs/.", Access.READ,
                params(Param.string("path", "File relative to the server root."),
                        Param.optionalInt("offset", "First line to return, starting at 1. Default 1."),
                        Param.optionalInt("limit", "Maximum number of lines. Default 2000.")),
                (args, caller) -> {
                    String content = fileManager.readFile(server(args, caller, Permission.FILES_VIEW), args.required("path"));
                    String[] lines = content.split("\n", -1);
                    int first = args.intValue("offset", 1, 1, Math.max(1, lines.length)) - 1;
                    int count = args.intValue("limit", 2000, 1, 20_000);
                    int end = Math.min(lines.length, first + count);
                    StringBuilder out = new StringBuilder();
                    if (first > 0 || end < lines.length) {
                        out.append("[lines ").append(first + 1).append('-').append(end).append(" of ").append(lines.length).append("]\n");
                    }
                    for (int i = first; i < end; i++) {
                        out.append(lines[i]);
                        if (i < end - 1) {
                            out.append('\n');
                        }
                    }
                    return out.toString();
                });

        add("search_files", "Find files of the server by name and/or text content. Skips worlds, libraries and CraftPanel's data.", Access.READ,
                params(Param.optionalString("path", "Folder to search in. Default: the root."),
                        Param.optionalString("name", "File name contains this text (case-insensitive)."),
                        Param.optionalString("text", "File content contains this text (case-insensitive)."),
                        Param.optionalInt("limit", "Maximum number of matches. Default 100, maximum 500.")),
                (args, caller) -> json(fileManager.searchFiles(server(args, caller, Permission.FILES_VIEW), args.optional("path"),
                        args.optional("name"), args.optional("text"), args.intValue("limit", 100, 1, 500))));

        add("write_file", "Create a text file or replace its whole content. Prefer edit_file for small changes to existing files.", Access.WRITE,
                params(Param.string("path", "File relative to the server root."), Param.string("content", "The full new content.")),
                (args, caller) -> {
                    String path = args.required("path");
                    String content = args.requiredText("content");
                    fileManager.writeFile(server(args, caller, Permission.FILES_EDIT), path, content);
                    return "Wrote " + content.length() + " characters to " + path;
                });

        add("edit_file", "Replace one exact piece of text in an existing text file. Fails unless that text occurs exactly once.", Access.WRITE,
                params(Param.string("path", "File relative to the server root."),
                        Param.string("old_text", "The exact text to replace, including enough context to be unique."),
                        Param.string("new_text", "Its replacement. May be empty.")),
                (args, caller) -> {
                    MinecraftServer server = server(args, caller, Permission.FILES_EDIT);
                    String path = args.required("path");
                    String oldText = args.required("old_text");
                    String content = fileManager.readFile(server, path);
                    int first = content.indexOf(oldText);
                    if (first < 0) {
                        throw new IllegalArgumentException("old_text was not found in " + path);
                    }
                    if (content.indexOf(oldText, first + 1) >= 0) {
                        throw new IllegalArgumentException("old_text occurs more than once in " + path + "; include more context");
                    }
                    String updated = content.substring(0, first) + args.requiredText("new_text") + content.substring(first + oldText.length());
                    fileManager.writeFile(server, path, updated);
                    return "Edited " + path;
                });

        add("create_directory", "Create a folder, including missing parents.", Access.WRITE,
                params(Param.string("path", "Folder relative to the server root.")),
                (args, caller) -> {
                    String path = args.required("path");
                    fileManager.createDirectory(server(args, caller, Permission.FILES_EDIT), path);
                    return "Created " + path;
                });

        add("rename_file", "Rename a file or folder, keeping it in the same folder.", Access.WRITE,
                params(Param.string("path", "Current path relative to the server root."), Param.string("new_name", "New name, without any folder.")),
                (args, caller) -> {
                    String path = args.required("path");
                    fileManager.renameFile(server(args, caller, Permission.FILES_EDIT), path, args.required("new_name"));
                    return "Renamed " + path;
                });

        add("move_file", "Move a file or folder to another folder, replacing any file with the same name there.", Access.DESTRUCTIVE,
                params(Param.string("path", "Current path relative to the server root."), Param.string("target_directory", "Existing destination folder.")),
                (args, caller) -> {
                    String path = args.required("path");
                    fileManager.moveFile(server(args, caller, Permission.FILES_EDIT), path, args.required("target_directory"));
                    return "Moved " + path;
                });

        add("delete_file", "Permanently delete a file or a whole folder. This cannot be undone.", Access.DESTRUCTIVE,
                params(Param.string("path", "Path relative to the server root.")),
                (args, caller) -> {
                    String path = args.required("path");
                    if (path.isBlank() || path.trim().equals("/") || path.trim().equals(".")) {
                        throw new IllegalArgumentException("Refusing to delete the server root");
                    }
                    fileManager.deleteFile(server(args, caller, Permission.FILES_DELETE), path);
                    return "Deleted " + path;
                });
    }

    private void registerPluginTools() {
        add("list_plugins", "List installed plugins with version, state and dependencies.", Access.READ, params(),
                (args, caller) -> json(pluginManager.listPlugins(server(args, caller, Permission.PLUGINS_VIEW))));

        add("toggle_plugin", "Enable a disabled plugin or disable an enabled one. Use the file name reported by list_plugins.", Access.WRITE,
                params(Param.string("filename", "Plugin file name, for example Example.jar or Example.jar.disabled.")),
                (args, caller) -> {
                    String filename = args.required("filename");
                    pluginManager.togglePlugin(server(args, caller, Permission.PLUGINS_MANAGE), filename);
                    return "Toggled " + filename;
                });

        add("load_plugin", "Load a plugin jar that is on disk but not running, without restarting.", Access.WRITE,
                params(Param.string("filename", "Plugin file name reported by list_plugins.")),
                (args, caller) -> {
                    String filename = args.required("filename");
                    if (!pluginManager.loadPlugin(server(args, caller, Permission.PLUGINS_MANAGE), filename)) {
                        throw new IllegalStateException("Could not load " + filename + "; check the console or restart the server.");
                    }
                    return "Loaded " + filename;
                });

        add("delete_plugin", "Delete a plugin jar (a backup copy is kept), optionally with its config folder.", Access.DESTRUCTIVE,
                params(Param.string("filename", "Plugin file name reported by list_plugins."),
                        Param.optionalBool("delete_config", "Also delete the plugin's config folder. Default false.")),
                (args, caller) -> {
                    String filename = args.required("filename");
                    PluginManager.RemovalResult result = pluginManager.deletePlugin(
                            server(args, caller, Permission.PLUGINS_MANAGE), filename, args.boolValue("delete_config"));
                    return result == PluginManager.RemovalResult.PENDING_RESTART
                            ? filename + " is in use by the server and will be removed when the server stops."
                            : "Deleted " + filename;
                });
    }

    private void registerBackupTools() {
        add("list_backups", "List the backups of a server.", Access.READ, params(),
                (args, caller) -> json(backupService.listBackups(server(args, caller, Permission.BACKUPS_VIEW).id())));

        add("create_backup", "Start a backup of the server files. It runs in the background; check it with get_task.", Access.WRITE,
                params(Param.optionalString("notes", "Short note stored with the backup.")),
                (args, caller) -> {
                    MinecraftServer server = server(args, caller, Permission.BACKUPS_MANAGE);
                    String notes = args.optional("notes");
                    Task task = taskManager.submit("BACKUP_CREATE", server.id(), "Creating backup of " + server.name(), (progress, message) -> {
                        BackupMetadata meta = backupService.createBackup(server, caller.user().username(), notes == null ? "" : notes, progress);
                        message.accept("Backup finished: " + meta.filename());
                    });
                    return json(task);
                });

        add("get_task", "Progress of a background task such as a backup.", Access.READ,
                List.of(Param.string("id", "Task id.")),
                (args, caller) -> {
                    Task task = taskManager.getTask(args.required("id"))
                            .orElseThrow(() -> new IllegalArgumentException("Task not found"));
                    if (task.targetId() != null && !userService.hasServerPermission(caller.user(), task.targetId(), Permission.SERVER_VIEW)) {
                        throw new SecurityException("Permission denied for this task");
                    }
                    return json(task);
                });
    }
}
