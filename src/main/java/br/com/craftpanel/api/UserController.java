package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserController {
    private final UserService userService;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public UserController(UserService userService, AuditService auditService, AuthMiddleware authMiddleware) {
        this.userService = userService;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    public void list(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.USERS_VIEW);
        List<User> users = userService.listUsers();
        ctx.json(users);
    }

    public void get(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.USERS_VIEW);
        String id = ctx.pathParam("id");
        User user = userService.getUser(id).orElseThrow(() -> new NotFoundResponse("User not found"));
        ctx.json(user);
    }

    public void create(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.USERS_MANAGE);
        User current = AuthMiddleware.requireUser(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        String username = body.path("username").asText(null);
        String password = body.path("password").asText(null);
        String displayName = body.path("displayName").asText(null);
        String roleId = body.path("roleId").asText("viewer");

        try {
            User created = userService.createUser(username, password, displayName, roleId);
            auditService.log(current.id(), current.username(), "USER_CREATE", "USER", created.id(), "Created user " + created.username(), ctx.ip(), "SUCCESS");
            ctx.status(201).json(created);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void update(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.USERS_MANAGE);
        User current = AuthMiddleware.requireUser(ctx);
        String id = ctx.pathParam("id");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        String displayName = body.path("displayName").asText(null);
        String roleId = body.path("roleId").asText(null);

        try {
            User updated = userService.updateUser(id, displayName, roleId);
            auditService.log(current.id(), current.username(), "USER_UPDATE", "USER", id, "Updated user " + updated.username(), ctx.ip(), "SUCCESS");
            ctx.json(updated);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void resetPassword(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.USERS_MANAGE);
        User current = AuthMiddleware.requireUser(ctx);
        String id = ctx.pathParam("id");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        String newPassword = body.path("newPassword").asText(null);
        if (newPassword == null || newPassword.length() < 8) {
            ctx.status(400).json(Map.of("error", "New password must be at least 8 characters"));
            return;
        }

        try {
            userService.resetPassword(id, newPassword);
            auditService.log(current.id(), current.username(), "USER_PASSWORD_RESET", "USER", id, "Reset password for user", ctx.ip(), "SUCCESS");
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void delete(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.USERS_MANAGE);
        User current = AuthMiddleware.requireUser(ctx);
        String id = ctx.pathParam("id");

        if (id.equals(current.id())) {
            ctx.status(400).json(Map.of("error", "Cannot delete your own account"));
            return;
        }

        try {
            userService.deleteUser(id);
            auditService.log(current.id(), current.username(), "USER_DELETE", "USER", id, "Deleted user account", ctx.ip(), "SUCCESS");
            ctx.json(Map.of("success", true));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void getServerPermissions(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.USERS_VIEW);
        String userId = ctx.pathParam("id");
        String serverId = ctx.pathParam("serverId");

        Set<String> perms = userService.getServerPermissions(userId, serverId);
        ctx.json(perms);
    }

    public void setServerPermissions(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.USERS_MANAGE);
        User current = AuthMiddleware.requireUser(ctx);
        String userId = ctx.pathParam("id");
        String serverId = ctx.pathParam("serverId");

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        List<String> perms = new ArrayList<>();
        if (body.isArray()) {
            for (JsonNode n : body) perms.add(n.asText());
        } else if (body.has("permissions") && body.get("permissions").isArray()) {
            for (JsonNode n : body.get("permissions")) perms.add(n.asText());
        }

        userService.setServerPermissions(userId, serverId, perms);
        auditService.log(current.id(), current.username(), "USER_SERVER_PERMS_UPDATE", "USER", userId, "Updated permissions for server " + serverId, ctx.ip(), "SUCCESS");
        ctx.json(Map.of("success", true));
    }
}
