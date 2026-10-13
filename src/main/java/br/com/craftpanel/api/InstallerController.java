package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.installer.ServerInstallerService;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.tasks.Task;
import br.com.craftpanel.tasks.TaskManager;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class InstallerController {
    private final ServerInstallerService installerService;
    private final TaskManager taskManager;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public InstallerController(ServerInstallerService installerService, TaskManager taskManager, AuditService auditService, AuthMiddleware authMiddleware) {
        this.installerService = installerService;
        this.taskManager = taskManager;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    public void getVersions(Context ctx) {
        String software = ctx.queryParam("software");
        if (software == null || software.isBlank()) {
            software = "paper";
        }
        List<String> versions = installerService.getAvailableVersions(software);
        ctx.json(versions);
    }

    public void getBuilds(Context ctx) {
        String software = ctx.queryParam("software");
        String version = ctx.queryParam("version");
        if (software == null || version == null) {
            ctx.status(400).json(Map.of("error", "software and version parameters are required"));
            return;
        }
        List<String> builds = installerService.getAvailableBuilds(software, version);
        ctx.json(builds);
    }

    public void install(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.SERVER_CREATE);
        User user = AuthMiddleware.requireUser(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        String name = body.path("name").asText(null);
        String description = body.path("description").asText("");
        String software = body.path("software").asText("paper");
        String mcVersion = body.path("minecraftVersion").asText("1.20.4");
        String build = body.path("buildNumber").asText("latest");
        int minMem = body.path("minMemoryMb").asInt(1024);
        int maxMem = body.path("maxMemoryMb").asInt(2048);
        int port = body.path("serverPort").asInt(25565);
        String jvmArgs = body.path("jvmArgs").asText("");
        boolean eula = body.path("acceptEula").asBoolean(true);

        if (name == null || name.isBlank()) {
            ctx.status(400).json(Map.of("error", "Server name is required"));
            return;
        }

        ServerInstallerService.InstallParams params = new ServerInstallerService.InstallParams(
                name, description, software, mcVersion, build, minMem, maxMem, port, jvmArgs, eula
        );

        Task task = taskManager.submit("SERVER_INSTALL", name, "Instalando servidor " + name + " (" + software + " " + mcVersion + ")", (progress, message) -> {
            MinecraftServer server = installerService.installServer(params, progress, message);
            auditService.log(user.id(), user.username(), "SERVER_INSTALL", "SERVER", server.id(), "Successfully installed server " + server.name(), ctx.ip(), "SUCCESS");
        });

        ctx.status(202).json(task);
    }
}
