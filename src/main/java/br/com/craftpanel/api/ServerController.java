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
import io.javalin.http.BadRequestResponse;
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
                ProcessState state = proc != null ? proc.getState() : ProcessState.RUNNING;
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
        res.put("status", proc != null ? proc.getState().name() : ProcessState.RUNNING.name());
        res.put("uptimeSeconds", proc != null ? proc.getUptime().toSeconds() : 0);
        res.put("metrics", metrics);

        ctx.json(res);
    }

    public void create(Context ctx) {
        throw new BadRequestResponse("Only one server instance exists in CraftPanel (the host server where the plugin is running).");
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
                body.path("autoStart").asBoolean(true)
        );

        auditService.log(user.id(), user.username(), "SERVER_UPDATE", "SERVER", id, "Updated host server configuration", ctx.ip(), "SUCCESS");
        ctx.json(updated);
    }

    public void delete(Context ctx) {
        throw new BadRequestResponse("The host server cannot be deleted.");
    }

    public void start(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_START);
        ctx.json(Map.of("success", true, "message", "Server is already running", "status", "RUNNING"));
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

        ServerProcess proc = processManager.getProcess(id);
        if (proc != null) {
            proc.restart(25);
            auditService.log(user.id(), user.username(), "SERVER_RESTART", "SERVER", id, "Requested server restart", ctx.ip(), "SUCCESS");
        }
        ctx.json(Map.of("success", true));
    }

    public void kill(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_KILL);
        User user = AuthMiddleware.requireUser(ctx);

        ServerProcess proc = processManager.getProcess(id);
        if (proc != null) {
            proc.kill();
            auditService.log(user.id(), user.username(), "SERVER_KILL", "SERVER", id, "Forcibly stopped server process", ctx.ip(), "SUCCESS");
        }
        ctx.json(Map.of("success", true));
    }

    public void status(Context ctx) {
        String id = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, id, Permission.SERVER_VIEW);

        ServerProcess proc = processManager.getProcess(id);
        ProcessState state = proc != null ? proc.getState() : ProcessState.RUNNING;
        long uptime = proc != null ? proc.getUptime().toSeconds() : 0;
        int exitCode = proc != null ? proc.getLastExitCode() : 0;

        ctx.json(Map.of(
                "status", state.name(),
                "uptimeSeconds", uptime,
                "exitCode", exitCode
        ));
    }
}
