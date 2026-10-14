package br.com.craftpanel;

import br.com.craftpanel.api.AuthController;
import br.com.craftpanel.api.AuthMiddleware;
import br.com.craftpanel.api.BackupController;
import br.com.craftpanel.api.FileController;
import br.com.craftpanel.api.InstallerController;
import br.com.craftpanel.api.ModrinthController;
import br.com.craftpanel.api.MonitoringController;
import br.com.craftpanel.api.PluginController;
import br.com.craftpanel.api.RoleController;
import br.com.craftpanel.api.ServerController;
import br.com.craftpanel.api.SetupController;
import br.com.craftpanel.api.TaskController;
import br.com.craftpanel.api.UserController;
import br.com.craftpanel.api.AuditController;
import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.auth.AuthService;
import br.com.craftpanel.backups.BackupService;
import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.console.ConsoleWebSocketHandler;
import br.com.craftpanel.console.EventsWebSocketHandler;
import br.com.craftpanel.database.DatabaseManager;
import br.com.craftpanel.files.FileManager;
import br.com.craftpanel.installer.ServerInstallerService;
import br.com.craftpanel.modrinth.ModrinthClient;
import br.com.craftpanel.monitoring.MonitoringService;
import br.com.craftpanel.monitoring.ServerMetrics;
import br.com.craftpanel.plugins.PluginManager;
import br.com.craftpanel.monitoring.MonitoringWebSocketHandler;
import br.com.craftpanel.processes.ProcessManager;
import br.com.craftpanel.processes.ProcessState;
import br.com.craftpanel.processes.ServerProcess;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerRepository;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.tasks.TaskManager;
import br.com.craftpanel.users.UserRepository;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CraftPanelServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CraftPanelServer.class);
    private static final CraftPanelServer INSTANCE = new CraftPanelServer();

    private Javalin app;
    private PanelConfig config;
    private DatabaseManager db;
    private UserRepository userRepo;
    private ServerRepository serverRepo;
    private AuthService authService;
    private UserService userService;
    private ServerService serverService;
    private ProcessManager processManager;
    private MonitoringWebSocketHandler monitoringWs;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    private CraftPanelServer() {}

    public static CraftPanelServer getInstance() {
        return INSTANCE;
    }

    private static volatile String initialSetupKey = null;

    public static String getInitialSetupKey() {
        return initialSetupKey;
    }

    public static void clearInitialSetupKey() {
        initialSetupKey = null;
    }

    private void announceInitialSetupKey(String key, PanelConfig cfg) {
        String url = "http://" + (cfg.server().host().equals("0.0.0.0") ? "127.0.0.1" : cfg.server().host()) + ":" + cfg.server().port();
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object consoleSender = bukkitClass.getMethod("getConsoleSender").invoke(null);
            if (consoleSender != null) {
                Class<?> chatColorClass = Class.forName("org.bukkit.ChatColor");
                String gold = String.valueOf(chatColorClass.getField("GOLD").get(null));
                String yellow = String.valueOf(chatColorClass.getField("YELLOW").get(null));
                String green = String.valueOf(chatColorClass.getField("GREEN").get(null));
                String aqua = String.valueOf(chatColorClass.getField("AQUA").get(null));
                String bold = String.valueOf(chatColorClass.getField("BOLD").get(null));
                String reset = String.valueOf(chatColorClass.getField("RESET").get(null));

                java.lang.reflect.Method sendMsg = consoleSender.getClass().getMethod("sendMessage", String.class);
                sendMsg.invoke(consoleSender, gold + "==================================================================" + reset);
                sendMsg.invoke(consoleSender, yellow + "[CraftPanel] " + bold + "INITIAL ADMINISTRATOR SETUP REQUIRED!" + reset);
                sendMsg.invoke(consoleSender, green + "[CraftPanel] Setup Security Key: " + aqua + bold + key + reset);
                sendMsg.invoke(consoleSender, yellow + "[CraftPanel] Enter this key on the web setup screen to claim the admin account." + reset);
                sendMsg.invoke(consoleSender, yellow + "[CraftPanel] Setup URL: " + aqua + url + reset);
                sendMsg.invoke(consoleSender, gold + "==================================================================" + reset);
            }
        } catch (Throwable ignored) {}
        LOGGER.warn("==================================================================");
        LOGGER.warn("[CraftPanel] INITIAL ADMINISTRATOR SETUP REQUIRED!");
        LOGGER.warn("[CraftPanel] Setup Security Key: {}", key);
        LOGGER.warn("[CraftPanel] Enter this key on the web setup screen to claim the admin account.");
        LOGGER.warn("[CraftPanel] Setup URL: {}", url);
        LOGGER.warn("==================================================================");
    }

    public synchronized void start(PanelConfig panelConfig) {
        if (running) {
            LOGGER.warn("CraftPanel server is already running");
            return;
        }

        this.config = panelConfig;
        LOGGER.info("Starting CraftPanel Web on {}:{}", config.server().host(), config.server().port());

        this.db = new DatabaseManager(config.storage().database());
        this.userRepo = new UserRepository(db);
        this.serverRepo = new ServerRepository(db);

        this.authService = new AuthService(userRepo, config);
        this.userService = new UserService(userRepo);
        this.serverService = new ServerService(serverRepo, config);
        this.processManager = new ProcessManager();
        FileManager fileManager = new FileManager();
        PluginManager pluginManager = new PluginManager(config);
        ModrinthClient modrinthClient = new ModrinthClient(config.modrinth().userAgent(), config.modrinth().apiUrl());
        ServerInstallerService installerService = new ServerInstallerService(serverService);
        BackupService backupService = new BackupService(db, config);
        MonitoringService monitoringService = new MonitoringService(processManager);
        TaskManager taskManager = new TaskManager(db);
        AuditService auditService = new AuditService(db);
        try {
            Class.forName("org.eclipse.jetty.io.ManagedSelector$CloseConnections");
            Class.forName("org.eclipse.jetty.io.ManagedSelector$StopSelector");
            Class.forName("org.eclipse.jetty.io.ManagedSelector$DestroyEndPoint");
            Class.forName("org.eclipse.jetty.servlet.BaseHolder$Wrapped");
            Class.forName("org.eclipse.jetty.servlet.FilterMapping");
        } catch (Throwable ignored) {}

        if (this.authService.isSetupRequired()) {
            String key = "CP-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            initialSetupKey = key;
            announceInitialSetupKey(key, config);
        }

        EventsWebSocketHandler eventsWs = new EventsWebSocketHandler(authService, userService, taskManager);
        AuthMiddleware authMiddleware = new AuthMiddleware(authService, userService);
        SetupController setupController = new SetupController(authService, auditService, config);
        AuthController authController = new AuthController(authService, userService, auditService, config);
        ServerController serverController = new ServerController(serverService, processManager, monitoringService, userService, auditService, authMiddleware);
        FileController fileController = new FileController(fileManager, serverService, auditService, authMiddleware, eventsWs);
        PluginController pluginController = new PluginController(pluginManager, serverService, auditService, authMiddleware);
        ModrinthController modrinthController = new ModrinthController(modrinthClient, serverService, pluginManager, taskManager, auditService, authMiddleware);
        InstallerController installerController = new InstallerController(installerService, taskManager, auditService, authMiddleware);
        BackupController backupController = new BackupController(backupService, serverService, processManager, taskManager, auditService, authMiddleware);
        UserController userController = new UserController(userService, auditService, authMiddleware);
        RoleController roleController = new RoleController(userService, auditService, authMiddleware);
        MonitoringController monitoringController = new MonitoringController(monitoringService, serverService, authMiddleware);
        AuditController auditController = new AuditController(auditService, authMiddleware);
        TaskController taskController = new TaskController(taskManager);

        ConsoleWebSocketHandler consoleWs = new ConsoleWebSocketHandler(authService, userService, serverService, processManager);
        this.monitoringWs = new MonitoringWebSocketHandler(authService, userService, monitoringService, serverService);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = CraftPanelServer.class.getClassLoader();
        }

        final ClassLoader finalCl = classLoader;
        this.app = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson(mapper, false));
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.precompress = false;
            });
            cfg.http.defaultContentType = "application/json";
        });

        app.before("/api/*", authMiddleware::handle);

        app.get("/api/setup/status", setupController::getStatus);
        app.post("/api/setup/first-admin", setupController::setupFirstAdmin);

        app.post("/api/auth/login", authController::login);
        app.post("/api/auth/logout", authController::logout);
        app.get("/api/auth/me", authController::me);
        app.post("/api/auth/change-password", authController::changePassword);
        app.post("/api/auth/ws-ticket", authController::createWsTicket);

        app.get("/api/servers", serverController::list);
        app.post("/api/servers", serverController::create);
        app.get("/api/servers/{id}", serverController::get);
        app.put("/api/servers/{id}", serverController::update);
        app.delete("/api/servers/{id}", serverController::delete);
        app.post("/api/servers/{id}/start", serverController::start);
        app.post("/api/servers/{id}/stop", serverController::stop);
        app.post("/api/servers/{id}/restart", serverController::restart);
        app.post("/api/servers/{id}/kill", serverController::kill);
        app.get("/api/servers/{id}/status", serverController::status);

        app.get("/api/servers/{id}/files", fileController::listFiles);
        app.get("/api/servers/{id}/files/read", fileController::readFile);
        app.post("/api/servers/{id}/files/write", fileController::writeFile);
        app.post("/api/servers/{id}/files/directory", fileController::createDirectory);
        app.post("/api/servers/{id}/files/create", fileController::createFile);
        app.post("/api/servers/{id}/files/rename", fileController::renameFile);
        app.post("/api/servers/{id}/files/move", fileController::moveFile);
        app.delete("/api/servers/{id}/files", fileController::deleteFile);
        app.get("/api/servers/{id}/files/download", fileController::downloadFile);
        app.post("/api/servers/{id}/files/upload", fileController::uploadFile);

        app.get("/api/servers/{id}/plugins", pluginController::listPlugins);
        app.post("/api/servers/{id}/plugins/toggle", pluginController::togglePlugin);
        app.post("/api/servers/{id}/plugins/load", pluginController::loadPlugin);
        app.delete("/api/servers/{id}/plugins", pluginController::deletePlugin);
        app.post("/api/servers/{id}/plugins/upload", pluginController::uploadPlugin);

        app.get("/api/modrinth/search", modrinthController::search);
        app.get("/api/modrinth/game-versions", modrinthController::getGameVersions);
        app.get("/api/modrinth/project/{idOrSlug}", modrinthController::getProject);
        app.get("/api/modrinth/project/{idOrSlug}/versions", modrinthController::getVersions);
        app.post("/api/servers/{id}/modrinth/install", modrinthController::install);
        app.post("/api/servers/{id}/modrinth/check-updates", modrinthController::checkUpdates);

        app.get("/api/installer/versions", installerController::getVersions);
        app.get("/api/installer/builds", installerController::getBuilds);
        app.post("/api/installer/install", installerController::install);

        app.get("/api/servers/{id}/backups", backupController::list);
        app.post("/api/servers/{id}/backups", backupController::create);
        app.post("/api/servers/{id}/backups/{backupId}/restore", backupController::restore);
        app.delete("/api/servers/{id}/backups/{backupId}", backupController::delete);
        app.get("/api/servers/{id}/backups/{backupId}/download", backupController::download);

        app.get("/api/users", userController::list);
        app.post("/api/users", userController::create);
        app.get("/api/users/{id}", userController::get);
        app.put("/api/users/{id}", userController::update);
        app.delete("/api/users/{id}", userController::delete);
        app.post("/api/users/{id}/reset-password", userController::resetPassword);
        app.get("/api/users/{id}/permissions/{serverId}", userController::getServerPermissions);
        app.put("/api/users/{id}/permissions/{serverId}", userController::setServerPermissions);

        app.get("/api/roles", roleController::listRoles);
        app.post("/api/roles", roleController::createRole);
        app.get("/api/roles/{id}", roleController::getRole);
        app.put("/api/roles/{id}", roleController::updateRole);
        app.delete("/api/roles/{id}", roleController::deleteRole);
        app.get("/api/permissions", roleController::listPermissions);

        app.get("/api/monitoring/system", monitoringController::getSystemMetrics);
        app.get("/api/servers/{id}/metrics", monitoringController::getServerMetrics);
        app.get("/api/servers/{id}/monitoring/spark", monitoringController::getSparkTelemetry);
        app.get("/api/servers/{id}/monitoring/history", monitoringController::getHistory);

        app.get("/api/audit", auditController::list);

        app.get("/api/tasks", taskController::list);
        app.get("/api/tasks/{id}", taskController::get);

        app.ws("/ws/console/{serverId}", consoleWs::configure);
        app.ws("/ws/events", eventsWs::configure);
        app.ws("/ws/monitoring/{serverId}", monitoringWs::configure);
        app.ws("/ws/monitoring", monitoringWs::configure);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "craftpanel-monitor");
            t.setDaemon(true);
            return t;
        });

        int interval = config.monitoring().refreshIntervalSeconds();
        this.scheduler.scheduleAtFixedRate(() -> {
            try {
                List<MinecraftServer> servers = serverService.listServers();
                List<Map<String, Object>> metricsList = new ArrayList<>();
                for (MinecraftServer server : servers) {
                    ServerProcess proc = processManager.getProcess(server.id());
                    if (proc != null && (proc.getState() == ProcessState.RUNNING || proc.getState() == ProcessState.STARTING)) {
                        ServerMetrics sm = monitoringService.collectMetrics(server);
                        Map<String, Object> item = new HashMap<>();
                        item.put("serverId", server.id());
                        item.put("name", server.name());
                        item.put("status", proc.getState().name());
                        item.put("metrics", sm);
                        metricsList.add(item);
                    }
                }
                eventsWs.broadcast("metrics_update", Map.of(
                        "system", monitoringService.collectSystemMetrics(),
                        "servers", metricsList
                ));
            } catch (Exception ignored) {}
        }, interval, interval, TimeUnit.SECONDS);

        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(CraftPanelServer.class.getClassLoader());
            this.app.start(config.server().host(), config.server().port());
            this.running = true;
            LOGGER.info("CraftPanel Web started successfully at http://{}:{}", config.server().host(), config.server().port());
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
        }

        for (MinecraftServer s : serverService.listServers()) {
            ServerProcess proc = processManager.getProcess(s);
            if (s.autoStart() && proc != null && !proc.isHostInstance()) {
                try {
                    LOGGER.info("Auto-starting configured server {}", s.name());
                    proc.start();
                } catch (Exception e) {
                    LOGGER.error("Failed to auto-start server {}", s.name(), e);
                }
            }
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        LOGGER.info("Stopping CraftPanel Web server...");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (monitoringWs != null) {
            monitoringWs.stop();
        }
        if (processManager != null) {
            processManager.shutdownAll();
        }
        if (app != null) {
            try {
                app.stop();
            } catch (Throwable ignored) {}
        }
        LOGGER.info("CraftPanel Web server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public PanelConfig getConfig() {
        return config;
    }

    public UserService getUserService() {
        return userService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public ServerService getServerService() {
        return serverService;
    }

    public ProcessManager getProcessManager() {
        return processManager;
    }
}
