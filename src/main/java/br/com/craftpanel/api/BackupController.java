package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.backups.BackupMetadata;
import br.com.craftpanel.backups.BackupService;
import br.com.craftpanel.processes.ProcessManager;
import br.com.craftpanel.processes.ProcessState;
import br.com.craftpanel.processes.ServerProcess;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.tasks.Task;
import br.com.craftpanel.tasks.TaskManager;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

public class BackupController {
    private final BackupService backupService;
    private final ServerService serverService;
    private final ProcessManager processManager;
    private final TaskManager taskManager;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public BackupController(
            BackupService backupService,
            ServerService serverService,
            ProcessManager processManager,
            TaskManager taskManager,
            AuditService auditService,
            AuthMiddleware authMiddleware
    ) {
        this.backupService = backupService;
        this.serverService = serverService;
        this.processManager = processManager;
        this.taskManager = taskManager;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    public void list(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.BACKUPS_VIEW);
        List<BackupMetadata> list = backupService.listBackups(serverId);
        ctx.json(list);
    }

    public void create(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.BACKUPS_MANAGE);
        User user = AuthMiddleware.requireUser(ctx);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String notes = body.path("notes").asText("");

        Task task = taskManager.submit("BACKUP_CREATE", serverId, "Criando backup para " + server.name(), (progress, message) -> {
            message.accept("Compactando arquivos da instância...");
            BackupMetadata meta = backupService.createBackup(server, user.username(), notes, progress);
            auditService.log(user.id(), user.username(), "BACKUP_CREATE", "BACKUP", meta.id(), "Backup created: " + meta.filename(), ctx.ip(), "SUCCESS");
            message.accept("Backup concluído com sucesso: " + meta.filename());
        });

        ctx.status(202).json(task);
    }

    public void restore(Context ctx) {
        String serverId = ctx.pathParam("id");
        String backupId = ctx.pathParam("backupId");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.BACKUPS_MANAGE);
        User user = AuthMiddleware.requireUser(ctx);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        ServerProcess proc = processManager.getProcess(serverId);
        if (proc != null && (proc.getState() == ProcessState.RUNNING || proc.getState() == ProcessState.STARTING)) {
            proc.stop(20);
        }

        Task task = taskManager.submit("BACKUP_RESTORE", serverId, "Restaurando backup na instância " + server.name(), (progress, message) -> {
            message.accept("Restaurando arquivos a partir do backup...");
            backupService.restoreBackup(server, backupId, progress);
            auditService.log(user.id(), user.username(), "BACKUP_RESTORE", "BACKUP", backupId, "Restored backup on " + server.name(), ctx.ip(), "SUCCESS");
            message.accept("Instância restaurada com sucesso.");
        });

        ctx.status(202).json(task);
    }

    public void delete(Context ctx) {
        String serverId = ctx.pathParam("id");
        String backupId = ctx.pathParam("backupId");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.BACKUPS_MANAGE);
        User user = AuthMiddleware.requireUser(ctx);

        backupService.deleteBackup(backupId);
        auditService.log(user.id(), user.username(), "BACKUP_DELETE", "BACKUP", backupId, "Deleted backup", ctx.ip(), "SUCCESS");
        ctx.json(Map.of("success", true));
    }

    public void download(Context ctx) {
        String serverId = ctx.pathParam("id");
        String backupId = ctx.pathParam("backupId");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.BACKUPS_MANAGE);

        try {
            File file = backupService.getBackupFile(backupId);
            ctx.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            ctx.result(new FileInputStream(file));
        } catch (Exception e) {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        }
    }
}
