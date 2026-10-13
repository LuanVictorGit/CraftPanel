package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.Role;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoleController {
    private final UserService userService;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public RoleController(UserService userService, AuditService auditService, AuthMiddleware authMiddleware) {
        this.userService = userService;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    public void listRoles(Context ctx) {
        List<Role> roles = userService.listRoles();
        ctx.json(roles);
    }

    public void getRole(Context ctx) {
        String id = ctx.pathParam("id");
        Role role = userService.getRole(id).orElseThrow(() -> new NotFoundResponse("Role not found"));
        ctx.json(role);
    }

    public void createRole(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.ROLES_MANAGE);
        User current = AuthMiddleware.requireUser(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        String name = body.path("name").asText(null);
        String description = body.path("description").asText("");
        List<String> perms = new ArrayList<>();
        if (body.path("permissions").isArray()) {
            for (JsonNode p : body.path("permissions")) perms.add(p.asText());
        }

        try {
            Role role = userService.createRole(name, description, perms);
            auditService.log(current.id(), current.username(), "ROLE_CREATE", "ROLE", role.id(), "Created role " + role.name(), ctx.ip(), "SUCCESS");
            ctx.status(201).json(role);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void updateRole(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.ROLES_MANAGE);
        User current = AuthMiddleware.requireUser(ctx);
        String id = ctx.pathParam("id");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        String name = body.path("name").asText(null);
        String description = body.path("description").asText("");
        List<String> perms = new ArrayList<>();
        if (body.path("permissions").isArray()) {
            for (JsonNode p : body.path("permissions")) perms.add(p.asText());
        }

        try {
            Role role = userService.updateRole(id, name, description, perms);
            auditService.log(current.id(), current.username(), "ROLE_UPDATE", "ROLE", role.id(), "Updated role " + role.name(), ctx.ip(), "SUCCESS");
            ctx.json(role);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void deleteRole(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.ROLES_MANAGE);
        User current = AuthMiddleware.requireUser(ctx);
        String id = ctx.pathParam("id");

        try {
            userService.deleteRole(id);
            auditService.log(current.id(), current.username(), "ROLE_DELETE", "ROLE", id, "Deleted custom role", ctx.ip(), "SUCCESS");
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void listPermissions(Context ctx) {
        ctx.json(Permission.ALL_PERMISSIONS);
    }
}
