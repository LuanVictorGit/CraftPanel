package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.files.FileItem;
import br.com.craftpanel.files.FileManager;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;

import br.com.craftpanel.console.EventsWebSocketHandler;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

public class FileController {
    private final FileManager fileManager;
    private final ServerService serverService;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;
    private final EventsWebSocketHandler eventsWs;

    public FileController(FileManager fileManager, ServerService serverService, AuditService auditService, AuthMiddleware authMiddleware, EventsWebSocketHandler eventsWs) {
        this.fileManager = fileManager;
        this.serverService = serverService;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
        this.eventsWs = eventsWs;
    }

    public void listFiles(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_VIEW);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        String path = ctx.queryParam("path");
        try {
            List<FileItem> items = fileManager.listFiles(server, path);
            ctx.json(items);
        } catch (SecurityException e) {
            ctx.status(403).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void readFile(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_VIEW);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        String path = ctx.queryParam("path");
        if (path == null || path.isBlank()) {
            ctx.status(400).json(Map.of("error", "File path parameter is required"));
            return;
        }

        try {
            FileManager.TextContent text = fileManager.readTextFile(server, path);
            ctx.json(Map.of("path", path, "content", text.content(), "charset", text.charset()));
        } catch (SecurityException e) {
            ctx.status(403).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void writeFile(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_EDIT);
        User user = AuthMiddleware.requireUser(ctx);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String path = body.path("path").asText(null);
        String content = body.path("content").asText("");
        String charset = body.path("charset").asText(null);

        if (path == null || path.isBlank()) {
            ctx.status(400).json(Map.of("error", "Path is required"));
            return;
        }

        try {
            fileManager.writeFile(server, path, content, charset);
            auditService.log(user.id(), user.username(), "FILE_WRITE", "FILE", path, "Edited file on server " + server.name(), ctx.ip(), "SUCCESS");
            if (eventsWs != null) {
                eventsWs.broadcast("file_change", Map.of(
                        "serverId", serverId,
                        "action", "write",
                        "path", path,
                        "content", content,
                        "user", user.username()
                ), serverId, Permission.FILES_VIEW);
            }
            ctx.json(Map.of("success", true));
        } catch (SecurityException e) {
            ctx.status(403).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void createDirectory(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_EDIT);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String path = body.path("path").asText(null);

        if (path == null || path.isBlank()) {
            ctx.status(400).json(Map.of("error", "Directory path is required"));
            return;
        }

        try {
            fileManager.createDirectory(server, path);
            if (eventsWs != null) {
                eventsWs.broadcast("file_change", Map.of(
                        "serverId", serverId,
                        "action", "create_dir",
                        "path", path
                ), serverId, Permission.FILES_VIEW);
            }
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void createFile(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_EDIT);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String path = body.path("path").asText(null);

        if (path == null || path.isBlank()) {
            ctx.status(400).json(Map.of("error", "File path is required"));
            return;
        }

        try {
            fileManager.createFile(server, path);
            if (eventsWs != null) {
                eventsWs.broadcast("file_change", Map.of(
                        "serverId", serverId,
                        "action", "create",
                        "path", path
                ), serverId, Permission.FILES_VIEW);
            }
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void renameFile(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_EDIT);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String path = body.path("path").asText(null);
        String newName = body.path("newName").asText(null);

        if (path == null || newName == null) {
            ctx.status(400).json(Map.of("error", "Path and newName are required"));
            return;
        }

        try {
            fileManager.renameFile(server, path, newName);
            if (eventsWs != null) {
                eventsWs.broadcast("file_change", Map.of(
                        "serverId", serverId,
                        "action", "rename",
                        "path", path,
                        "newName", newName
                ), serverId, Permission.FILES_VIEW);
            }
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void moveFile(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_EDIT);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String source = body.path("sourcePath").asText(null);
        String targetDir = body.path("targetDir").asText(null);

        if (source == null || targetDir == null) {
            ctx.status(400).json(Map.of("error", "sourcePath and targetDir are required"));
            return;
        }

        try {
            fileManager.moveFile(server, source, targetDir);
            if (eventsWs != null) {
                eventsWs.broadcast("file_change", Map.of(
                        "serverId", serverId,
                        "action", "move",
                        "source", source,
                        "targetDir", targetDir
                ), serverId, Permission.FILES_VIEW);
            }
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void deleteFile(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_DELETE);
        User user = AuthMiddleware.requireUser(ctx);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        String path = ctx.queryParam("path");
        if ((path == null || path.isBlank()) && ctx.body() != null && !ctx.body().isBlank()) {
            try {
                JsonNode body = ctx.bodyAsClass(JsonNode.class);
                if (body != null && body.has("path")) {
                    path = body.path("path").asText(null);
                }
            } catch (Exception ignored) {}
        }
        if (path == null || path.isBlank()) {
            ctx.status(400).json(Map.of("error", "File path parameter is required"));
            return;
        }

        try {
            fileManager.deleteFile(server, path);
            auditService.log(user.id(), user.username(), "FILE_DELETE", "FILE", path, "Deleted file on server " + server.name(), ctx.ip(), "SUCCESS");
            if (eventsWs != null) {
                eventsWs.broadcast("file_change", Map.of(
                        "serverId", serverId,
                        "action", "delete",
                        "path", path,
                        "user", user.username()
                ), serverId, Permission.FILES_VIEW);
            }
            ctx.json(Map.of("success", true));
        } catch (SecurityException e) {
            ctx.status(403).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void downloadFile(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_DOWNLOAD);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        String path = ctx.queryParam("path");
        if (path == null || path.isBlank()) {
            ctx.status(400).json(Map.of("error", "Path is required"));
            return;
        }

        try {
            File file = fileManager.getFile(server, path);
            ctx.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            ctx.result(new FileInputStream(file));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void uploadFile(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.FILES_UPLOAD);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        String targetDir = ctx.formParam("targetDir");
        UploadedFile uploaded = ctx.uploadedFile("file");

        if (uploaded == null) {
            ctx.status(400).json(Map.of("error", "No file uploaded"));
            return;
        }

        try {
            fileManager.saveUploadedFile(server, targetDir != null ? targetDir : "", uploaded.filename(), uploaded.content());
            if (eventsWs != null) {
                eventsWs.broadcast("file_change", Map.of(
                        "serverId", serverId,
                        "action", "upload",
                        "targetDir", targetDir != null ? targetDir : "",
                        "filename", uploaded.filename()
                ), serverId, Permission.FILES_VIEW);
            }
            ctx.json(Map.of("success", true, "filename", uploaded.filename()));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }
}
