package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.installer.ServerInstallerService;
import br.com.craftpanel.tasks.TaskManager;
import io.javalin.http.BadRequestResponse;
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
        throw new BadRequestResponse("Installing additional servers is disabled. CraftPanel manages the single host Minecraft server where the plugin is running.");
    }
}
