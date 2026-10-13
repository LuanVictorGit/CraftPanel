package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.plugins.InstalledPlugin;
import br.com.craftpanel.plugins.PluginManager;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;

import java.util.List;
import java.util.Map;

public class PluginController {
    private final PluginManager pluginManager;
    private final ServerService serverService;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public PluginController(PluginManager pluginManager, ServerService serverService, AuditService auditService, AuthMiddleware authMiddleware) {
        this.pluginManager = pluginManager;
        this.serverService = serverService;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    public void listPlugins(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.PLUGINS_VIEW);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        List<InstalledPlugin> list = pluginManager.listPlugins(server);
        ctx.json(list);
    }

    public void togglePlugin(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.PLUGINS_MANAGE);
        User user = AuthMiddleware.requireUser(ctx);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String filename = body.path("filename").asText(null);

        if (filename == null || filename.isBlank()) {
            ctx.status(400).json(Map.of("error", "Filename is required"));
            return;
        }

        try {
            pluginManager.togglePlugin(server, filename);
            auditService.log(user.id(), user.username(), "PLUGIN_TOGGLE", "PLUGIN", filename, "Toggled plugin status on server " + server.name(), ctx.ip(), "SUCCESS");
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void deletePlugin(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.PLUGINS_MANAGE);
        User user = AuthMiddleware.requireUser(ctx);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        String filename = ctx.queryParam("filename");
        if (filename == null || filename.isBlank()) {
            ctx.status(400).json(Map.of("error", "Filename is required"));
            return;
        }

        try {
            pluginManager.deletePlugin(server, filename);
            auditService.log(user.id(), user.username(), "PLUGIN_DELETE", "PLUGIN", filename, "Deleted plugin with backup on server " + server.name(), ctx.ip(), "SUCCESS");
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void uploadPlugin(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.PLUGINS_MANAGE);
        User user = AuthMiddleware.requireUser(ctx);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        UploadedFile uploaded = ctx.uploadedFile("file");
        if (uploaded == null) {
            ctx.status(400).json(Map.of("error", "No plugin file provided"));
            return;
        }

        try {
            InstalledPlugin plugin = pluginManager.installPlugin(server, uploaded.filename(), uploaded.content());
            auditService.log(user.id(), user.username(), "PLUGIN_UPLOAD", "PLUGIN", uploaded.filename(), "Uploaded plugin " + plugin.name() + " on server " + server.name(), ctx.ip(), "SUCCESS");
            ctx.json(plugin);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }
}
