package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.modrinth.CompatibilityChecker;
import br.com.craftpanel.modrinth.CompatibilityResult;
import br.com.craftpanel.modrinth.ModrinthClient;
import br.com.craftpanel.modrinth.ModrinthProject;
import br.com.craftpanel.modrinth.ModrinthVersion;
import br.com.craftpanel.plugins.InstalledPlugin;
import br.com.craftpanel.plugins.PluginManager;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.tasks.Task;
import br.com.craftpanel.tasks.TaskManager;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ModrinthController {
    private final ModrinthClient modrinthClient;
    private final ServerService serverService;
    private final PluginManager pluginManager;
    private final TaskManager taskManager;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public ModrinthController(
            ModrinthClient modrinthClient,
            ServerService serverService,
            PluginManager pluginManager,
            TaskManager taskManager,
            AuditService auditService,
            AuthMiddleware authMiddleware
    ) {
        this.modrinthClient = modrinthClient;
        this.serverService = serverService;
        this.pluginManager = pluginManager;
        this.taskManager = taskManager;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    public void search(Context ctx) {
        String query = ctx.queryParam("query");
        String version = ctx.queryParam("version");
        String loader = ctx.queryParam("loader");
        String index = ctx.queryParam("index");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);

        try {
            ModrinthClient.SearchResult result = modrinthClient.search(query, version, loader, index, limit, offset);
            ctx.json(result);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Modrinth search failed: " + e.getMessage()));
        }
    }

    public void getProject(Context ctx) {
        String idOrSlug = ctx.pathParam("idOrSlug");
        Optional<ModrinthProject> project = modrinthClient.getProject(idOrSlug);
        if (project.isEmpty()) {
            throw new NotFoundResponse("Project not found");
        }
        ctx.json(project.get());
    }

    public void getVersions(Context ctx) {
        String idOrSlug = ctx.pathParam("idOrSlug");
        String gameVersion = ctx.queryParam("gameVersion");
        String loader = ctx.queryParam("loader");
        String serverId = ctx.queryParam("serverId");

        try {
            List<ModrinthVersion> versions = modrinthClient.getProjectVersions(idOrSlug, gameVersion, loader);
            if (serverId != null && !serverId.isBlank()) {
                Optional<MinecraftServer> serverOpt = serverService.getServer(serverId);
                if (serverOpt.isPresent()) {
                    MinecraftServer server = serverOpt.get();
                    List<Map<String, Object>> enriched = new ArrayList<>();
                    for (ModrinthVersion ver : versions) {
                        CompatibilityResult comp = CompatibilityChecker.check(server.software(), server.minecraftVersion(), ver);
                        Map<String, Object> map = new HashMap<>();
                        map.put("version", ver);
                        map.put("compatibility", comp);
                        enriched.add(map);
                    }
                    ctx.json(enriched);
                    return;
                }
            }
            ctx.json(versions);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Failed to fetch versions: " + e.getMessage()));
        }
    }

    public void install(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.MODRINTH_INSTALL);
        User user = AuthMiddleware.requireUser(ctx);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String fileUrl = body.path("fileUrl").asText(null);
        String filename = body.path("filename").asText(null);
        String expectedHash = body.path("hash").asText(null);
        String projectName = body.path("projectName").asText(filename);

        if (fileUrl == null || filename == null) {
            ctx.status(400).json(Map.of("error", "fileUrl and filename are required"));
            return;
        }

        Task task = taskManager.submit("MODRINTH_INSTALL", serverId, "Instalando plugin " + projectName, (progress, message) -> {
            message.accept("Preparando diretório de plugins...");
            Path pluginsDir = Paths.get(server.serverDir(), "plugins");
            Files.createDirectories(pluginsDir);

            Path targetFile = pluginsDir.resolve(Paths.get(filename).getFileName().toString());
            if (Files.exists(targetFile)) {
                message.accept("Criando backup da versão atual...");
                Path backupDir = Paths.get(server.serverDir()).resolveSibling("backups").resolve("plugins");
                Files.createDirectories(backupDir);
                Path backupFile = backupDir.resolve(targetFile.getFileName().toString() + "." + System.currentTimeMillis() + ".bak");
                Files.copy(targetFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            message.accept("Baixando artefato oficial do Modrinth...");
            modrinthClient.downloadFile(fileUrl, targetFile, progress, expectedHash);
            message.accept("Plugin instalado e validado com sucesso: " + filename);
        });

        auditService.log(user.id(), user.username(), "MODRINTH_INSTALL", "PLUGIN", filename, "Submitted Modrinth install task for " + projectName, ctx.ip(), "SUCCESS");
        ctx.status(202).json(task);
    }

    public void checkUpdates(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.PLUGINS_VIEW);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        List<InstalledPlugin> installed = pluginManager.listPlugins(server);
        List<Map<String, Object>> results = new ArrayList<>();

        for (InstalledPlugin p : installed) {
            try {
                ModrinthClient.SearchResult search = modrinthClient.search(p.name(), server.minecraftVersion(), server.software(), "relevance", 1, 0);
                if (!search.hits().isEmpty()) {
                    ModrinthProject match = search.hits().get(0);
                    List<ModrinthVersion> vers = modrinthClient.getProjectVersions(match.id(), server.minecraftVersion(), server.software());
                    if (!vers.isEmpty()) {
                        ModrinthVersion latestVer = vers.get(0);
                        boolean hasUpdate = !latestVer.versionNumber().equalsIgnoreCase(p.version());
                        results.add(Map.of(
                                "installed", p,
                                "modrinthProject", match,
                                "latestVersion", latestVer,
                                "hasUpdate", hasUpdate
                        ));
                    }
                }
            } catch (Exception ignored) {}
        }

        ctx.json(results);
    }
}
