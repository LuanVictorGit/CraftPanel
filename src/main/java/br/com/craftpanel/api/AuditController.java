package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditLog;
import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.users.Permission;
import io.javalin.http.Context;

import java.util.List;

public class AuditController {
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public AuditController(AuditService auditService, AuthMiddleware authMiddleware) {
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    public void list(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.AUDIT_VIEW);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        String search = ctx.queryParam("search");

        List<AuditLog> logs = auditService.listLogs(limit, offset, search);
        ctx.json(logs);
    }
}
