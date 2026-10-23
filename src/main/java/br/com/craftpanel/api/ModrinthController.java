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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // For a server, only plugins made for its platform and Minecraft version are listed.
        Optional<MinecraftServer> server = serverOf(ctx);
        if (server.isPresent()) {
            loader = server.get().software();
            version = server.get().minecraftVersion();
        }

        try {
            ModrinthClient.SearchResult result = modrinthClient.search(query, version, loader, index, limit, offset);
            ctx.json(result);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Modrinth search failed: " + e.getMessage()));
        }
    }

    public void getGameVersions(Context ctx) {
        ctx.json(modrinthClient.getGameVersions());
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

        try {
            Optional<MinecraftServer> serverOpt = serverOf(ctx);
            if (serverOpt.isPresent()) {
                // Only the builds confirmed for the server's platform and Minecraft version can be installed.
                MinecraftServer server = serverOpt.get();
                List<Map<String, Object>> compatible = new ArrayList<>();
                for (ModrinthVersion ver : modrinthClient.getProjectVersions(idOrSlug, server.minecraftVersion(), server.software())) {
                    CompatibilityResult comp = CompatibilityChecker.check(server.software(), server.minecraftVersion(), ver);
                    if (comp.status() == CompatibilityResult.Status.COMPATIBLE_CONFIRMED) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("version", ver);
                        map.put("compatibility", comp);
                        compatible.add(map);
                    }
                }
                ctx.json(compatible);
                return;
            }
            ctx.json(modrinthClient.getProjectVersions(idOrSlug, gameVersion, loader));
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

        String safeName;
        try {
            safeName = PluginManager.sanitizeJarName(filename);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
            return;
        }
        // The jar gets loaded into the server, so only accept Modrinth's own CDN (MODRINTH_INSTALL is not PLUGINS_MANAGE).
        if (!isModrinthCdnUrl(fileUrl)) {
            ctx.status(400).json(Map.of("error", "Only files hosted on the Modrinth CDN can be installed"));
            return;
        }
        if (expectedHash == null || !expectedHash.trim().matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{128}")) {
            ctx.status(400).json(Map.of("error", "The file hash published by Modrinth is required"));
            return;
        }

        // The file must be a build Modrinth lists for this server's platform and Minecraft version.
        try {
            Optional<ModrinthVersion> published = modrinthClient.getVersionByFileHash(expectedHash.trim());
            if (published.isEmpty()) {
                ctx.status(400).json(Map.of("error", "This file is not published on Modrinth"));
                return;
            }
            CompatibilityResult comp = CompatibilityChecker.check(server.software(), server.minecraftVersion(), published.get());
            if (comp.status() != CompatibilityResult.Status.COMPATIBLE_CONFIRMED) {
                ctx.status(400).json(Map.of("error", "This version is not compatible with " + server.software() + " "
                        + server.minecraftVersion() + ": " + comp.message()));
                return;
            }
        } catch (Exception e) {
            ctx.status(502).json(Map.of("error", "Could not check the version on Modrinth: " + e.getMessage()));
            return;
        }

        Task task = taskManager.submit("MODRINTH_INSTALL", serverId, "Instalando plugin " + projectName, (progress, message) -> {
            message.accept("Preparando diretório de plugins...");
            Path pluginsDir = pluginManager.getPluginsDir(server).toPath();
            Files.createDirectories(pluginsDir);

            Path targetFile = pluginsDir.resolve(safeName);
            Path stagedFile = pluginsDir.resolve(safeName + ".part");
            try {
                message.accept("Baixando artefato oficial do Modrinth...");
                modrinthClient.downloadFile(fileUrl, stagedFile, progress, expectedHash);

                message.accept("Substituindo versão atual (backup automático)...");
                List<String> replaced = pluginManager.replacePluginJar(server, stagedFile, targetFile);
                if (!replaced.isEmpty()) {
                    message.accept("Versões anteriores removidas: " + String.join(", ", replaced));
                }
            } finally {
                Files.deleteIfExists(stagedFile);
            }

            message.accept("Plugin instalado e validado com sucesso: " + safeName);
            pluginManager.loadPluginAtRuntime(targetFile.toFile());
        });

        auditService.log(user.id(), user.username(), "MODRINTH_INSTALL", "PLUGIN", safeName, "Submitted Modrinth install task for " + projectName, ctx.ip(), "SUCCESS");
        ctx.status(202).json(task);
    }

    private Optional<MinecraftServer> serverOf(Context ctx) {
        String serverId = ctx.queryParam("serverId");
        return serverId == null || serverId.isBlank() ? Optional.empty() : serverService.getServer(serverId);
    }

    private static boolean isModrinthCdnUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            return "https".equalsIgnoreCase(uri.getScheme())
                    && (host.equals("cdn.modrinth.com") || host.equals("cdn-raw.modrinth.com"));
        } catch (IllegalArgumentException e) {
            return false;
        }
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
