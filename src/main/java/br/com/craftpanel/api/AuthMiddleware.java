package br.com.craftpanel.api;

import br.com.craftpanel.auth.AuthService;
import br.com.craftpanel.auth.Session;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.ForbiddenResponse;

import java.util.Optional;

public class AuthMiddleware {
    private final AuthService authService;
    private final UserService userService;

    public AuthMiddleware(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    public void handle(Context ctx) {
        String token = ctx.cookie("craftpanel_session");
        if (token == null || token.isBlank()) {
            String authHeader = ctx.header("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            }
        }

        if (token != null && !token.isBlank()) {
            Optional<Session> session = authService.validateSession(token);
            if (session.isPresent()) {
                Optional<User> user = userService.getUser(session.get().userId());
                if (user.isPresent()) {
                    ctx.attribute("user", user.get());
                    ctx.attribute("session", session.get());
                }
            }
        }
    }

    public static User getUser(Context ctx) {
        return ctx.attribute("user");
    }

    public static User requireUser(Context ctx) {
        User user = getUser(ctx);
        if (user == null) {
            throw new UnauthorizedResponse("Authentication required");
        }
        return user;
    }

    public void requirePermission(Context ctx, String permission) {
        User user = requireUser(ctx);
        if (!userService.hasPermission(user, permission)) {
            throw new ForbiddenResponse("Permission denied: " + permission);
        }
    }

    public void requireServerPermission(Context ctx, String serverId, String permission) {
        User user = requireUser(ctx);
        if (!userService.hasServerPermission(user, serverId, permission)) {
            throw new ForbiddenResponse("Permission denied: " + permission + " for server " + serverId);
        }
    }
}
