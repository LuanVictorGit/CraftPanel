package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.auth.AuthService;
import br.com.craftpanel.auth.Session;
import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.users.Role;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final AuditService auditService;
    private final PanelConfig config;

    public AuthController(AuthService authService, UserService userService, AuditService auditService, PanelConfig config) {
        this.authService = authService;
        this.userService = userService;
        this.auditService = auditService;
        this.config = config;
    }

    public void login(Context ctx) {
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String username = body.path("username").asText(null);
        String password = body.path("password").asText(null);

        if (username == null || password == null) {
            ctx.status(400).json(Map.of("error", "Username and password are required"));
            return;
        }

        try {
            Session session = authService.login(username, password, ctx.ip(), ctx.userAgent());
            Optional<User> userOpt = userService.getUser(session.userId());

            Cookie cookie = new Cookie("craftpanel_session", session.token());
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setSecure(config.security().requireSecureCookies());
            cookie.setSameSite(SameSite.LAX);
            ctx.cookie(cookie);

            auditService.log(session.userId(), username, "AUTH_LOGIN", "SESSION", session.token(), "Login successful", ctx.ip(), "SUCCESS");

            ctx.json(Map.of(
                    "success", true,
                    "token", session.token(),
                    "user", userOpt.orElse(null),
                    "version", br.com.craftpanel.CraftPanelVersion.getVersion()
            ));
        } catch (SecurityException e) {
            auditService.log(null, username, "AUTH_LOGIN_FAILED", "USER", username, e.getMessage(), ctx.ip(), "FAILURE");
            ctx.status(401).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Internal server error during authentication"));
        }
    }

    public void logout(Context ctx) {
        Session session = ctx.attribute("session");
        if (session != null) {
            authService.logout(session.token());
            auditService.log(session.userId(), session.username(), "AUTH_LOGOUT", "SESSION", session.token(), "User logged out", ctx.ip(), "SUCCESS");
        }

        Cookie cookie = new Cookie("craftpanel_session", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        ctx.cookie(cookie);

        ctx.json(Map.of("success", true));
    }

    public void me(Context ctx) {
        User user = AuthMiddleware.requireUser(ctx);
        Optional<Role> role = userService.getRole(user.roleId());

        ctx.json(Map.of(
                "user", user,
                "role", role.orElse(null),
                "permissions", role.map(Role::permissions).orElse(Collections.emptyList()),
                "version", br.com.craftpanel.CraftPanelVersion.getVersion()
        ));
    }

    public void changePassword(Context ctx) {
        User user = AuthMiddleware.requireUser(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String oldPass = body.path("oldPassword").asText(null);
        String newPass = body.path("newPassword").asText(null);

        if (oldPass == null || newPass == null) {
            ctx.status(400).json(Map.of("error", "Old password and new password are required"));
            return;
        }

        try {
            authService.changePassword(user.id(), oldPass, newPass);
            auditService.log(user.id(), user.username(), "PASSWORD_CHANGE", "USER", user.id(), "Password changed successfully", ctx.ip(), "SUCCESS");
            ctx.json(Map.of("success", true));
        } catch (SecurityException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void createWsTicket(Context ctx) {
        User user = AuthMiddleware.requireUser(ctx);
        String ticket = authService.createWebSocketTicket(user.id());
        ctx.json(Map.of("ticket", ticket));
    }
}
