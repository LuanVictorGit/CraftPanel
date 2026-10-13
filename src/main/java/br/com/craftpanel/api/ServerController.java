package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.monitoring.MonitoringService;
import br.com.craftpanel.monitoring.ServerMetrics;
import br.com.craftpanel.processes.ProcessManager;
import br.com.craftpanel.processes.ProcessState;
import br.com.craftpanel.processes.ServerProcess;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerController {
    private final ServerService serverService;
    private final ProcessManager processManager;
    private final MonitoringService monitoringService;
    private final UserService userService;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public ServerController(
            ServerService serverService,
            ProcessManager processManager,
            MonitoringService monitoringService,
            UserService userService,
            AuditService auditService,
            AuthMiddleware authMiddleware
    ) {
        this.serverService = serverService;
        this.processManager = processManager;
        this.monitoringService = monitoringService;
        this.userService = userService;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    public void list(Context ctx) {
        User user = AuthMiddleware.requireUser(ctx);
        List<MinecraftServer> all = serverService.listServers();
        List<Map<String, Object>> result = new ArrayList<>();

        for (MinecraftServer server : all) {
            if (userService.hasServerPermission(user, server.id(), Permission.SERVER_VIEW)) {
                ServerProcess proc = processManager.getProcess(server);
                ProcessState state = proc != null ? proc.getState() : ProcessState.STOPPED;
                ServerMetrics metrics = monitoringService.collectMetrics(server);

                Map<String, Object> map = new HashMap<>();
                map.put("id", server.id());
                map.put("name", server.name());
                map.put("description", server.description());
                map.put("software", server.software());
                map.put("minecraftVersion", server.minecraftVersion());
                map.put("serverPort", server.serverPort());
                map.put("minMemoryMb", server.minMemoryMb());
                map.put("maxMemoryMb", server.maxMemoryMb());
                map.put("autoStart", server.autoStart());
                map.put("status", state.name());
                map.put("uptimeSeconds", proc != null ? proc.getUptime().toSeconds() : 0);
                map.put("onlinePlayers", metrics != null ? metrics.playerCount() : 0);
                map.put("maxPlayers", metrics != null ? metrics.maxPlayers() : 0);
                map.put("cpuPercent", metrics != null ? metrics.cpuPercent() : 0.0);
                map.put("memoryUsedMb", metrics != null ? metrics.memoryUsedMb() : 0);

                result.add(map);
            }
        }

        ctx.json(result);
    }

    public void get(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_VIEW);

        MinecraftServer server = serverService.getServer(id).orElseThrow(() -> new NotFoundResponse("Server not found"));
        ServerProcess proc = processManager.getProcess(server);
        ServerMetrics metrics = monitoringService.collectMetrics(server);

        Map<String, Object> res = new HashMap<>();
        res.put("server", server);
        res.put("status", proc != null ? proc.getState().name() : ProcessState.STOPPED.name());
        res.put("uptimeSeconds", proc != null ? proc.getUptime().toSeconds() : 0);
        res.put("metrics", metrics);

        ctx.json(res);
    }

    public void create(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.SERVER_CREATE);
        User user = AuthMiddleware.requireUser(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        MinecraftServer created = serverService.createServer(
                body.path("name").asText(),
                body.path("description").asText(""),
                body.path("software").asText("paper"),
                body.path("minecraftVersion").asText("1.20.4"),
                body.path("jarPath").asText(null),
                body.path("serverDir").asText(null),
                body.path("javaExecutable").asText("java"),
                body.path("minMemoryMb").asInt(1024),
                body.path("maxMemoryMb").asInt(2048),
                body.path("serverPort").asInt(25565),
                body.path("jvmArgs").asText(""),
                body.path("autoStart").asBoolean(false)
        );

        auditService.log(user.id(), user.username(), "SERVER_CREATE", "SERVER", created.id(), "Created server " + created.name(), ctx.ip(), "SUCCESS");
        ctx.status(201).json(created);
    }

    public void update(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_EDIT);
        User user = AuthMiddleware.requireUser(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        MinecraftServer updated = serverService.updateServer(
                id,
                body.path("name").asText(),
                body.path("description").asText(""),
                body.path("software").asText(),
                body.path("minecraftVersion").asText(),
                body.path("jarPath").asText(),
                body.path("serverDir").asText(),
                body.path("javaExecutable").asText("java"),
                body.path("minMemoryMb").asInt(1024),
                body.path("maxMemoryMb").asInt(2048),
                body.path("serverPort").asInt(25565),
                body.path("jvmArgs").asText(""),
                body.path("autoStart").asBoolean(false)
        );

        auditService.log(user.id(), user.username(), "SERVER_UPDATE", "SERVER", id, "Updated server " + updated.name(), ctx.ip(), "SUCCESS");
        ctx.json(updated);
    }

    public void delete(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_DELETE);
        User user = AuthMiddleware.requireUser(ctx);

        ServerProcess proc = processManager.getProcess(id);
        if (proc != null && (proc.getState() == ProcessState.RUNNING || proc.getState() == ProcessState.STARTING)) {
            proc.kill();
        }
        processManager.removeProcess(id);

        boolean deleteFiles = ctx.queryParamAsClass("deleteFiles", Boolean.class).getOrDefault(false);
        serverService.deleteServer(id, deleteFiles);

        auditService.log(user.id(), user.username(), "SERVER_DELETE", "SERVER", id, "Deleted server (files deleted: " + deleteFiles + ")", ctx.ip(), "SUCCESS");
        ctx.json(Map.of("success", true));
    }

    public void start(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_START);
        User user = AuthMiddleware.requireUser(ctx);

        MinecraftServer server = serverService.getServer(id).orElseThrow(() -> new NotFoundResponse("Server not found"));
        ServerProcess proc = processManager.getProcess(server);

        try {
            proc.start();
            auditService.log(user.id(), user.username(), "SERVER_START", "SERVER", id, "Started server process", ctx.ip(), "SUCCESS");
            ctx.json(Map.of("success", true, "status", proc.getState().name()));
        } catch (Exception e) {
            auditService.log(user.id(), user.username(), "SERVER_START_FAILED", "SERVER", id, e.getMessage(), ctx.ip(), "FAILURE");
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void stop(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_STOP);
        User user = AuthMiddleware.requireUser(ctx);

        ServerProcess proc = processManager.getProcess(id);
        if (proc != null) {
            proc.stop(25);
            auditService.log(user.id(), user.username(), "SERVER_STOP", "SERVER", id, "Requested graceful stop", ctx.ip(), "SUCCESS");
        }
        ctx.json(Map.of("success", true));
    }

    public void restart(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_RESTART);
        User user = AuthMiddleware.requireUser(ctx);

        MinecraftServer server = serverService.getServer(id).orElseThrow(() -> new NotFoundResponse("Server not found"));
        ServerProcess proc = processManager.getProcess(server);
        proc.restart(25);

        auditService.log(user.id(), user.username(), "SERVER_RESTART", "SERVER", id, "Requested server restart", ctx.ip(), "SUCCESS");
        ctx.json(Map.of("success", true));
    }

    public void kill(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_KILL);
        User user = AuthMiddleware.requireUser(ctx);

        ServerProcess proc = processManager.getProcess(id);
        if (proc != null) {
            proc.kill();
            auditService.log(user.id(), user.username(), "SERVER_KILL", "SERVER", id, "Forcibly killed server process", ctx.ip(), "SUCCESS");
        }
        ctx.json(Map.of("success", true));
    }

    public void status(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_VIEW);

        ServerProcess proc = processManager.getProcess(id);
        ProcessState state = proc != null ? proc.getState() : ProcessState.STOPPED;
        long uptime = proc != null ? proc.getUptime().toSeconds() : 0;
        int exitCode = proc != null ? proc.getLastExitCode() : 0;

        ctx.json(Map.of(
                "status", state.name(),
                "uptimeSeconds", uptime,
                "exitCode", exitCode
        ));
    }
}
