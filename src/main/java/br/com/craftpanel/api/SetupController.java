package br.com.craftpanel.api;

import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.auth.AuthService;
import br.com.craftpanel.auth.Session;
import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.users.User;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;

import java.util.Map;

public class SetupController {
    private final AuthService authService;
    private final AuditService auditService;
    private final PanelConfig config;

    public SetupController(AuthService authService, AuditService auditService, PanelConfig config) {
        this.authService = authService;
        this.auditService = auditService;
        this.config = config;
    }

    public void getStatus(Context ctx) {
        ctx.json(Map.of(
                "setupRequired", authService.isSetupRequired(),
                "version", br.com.craftpanel.CraftPanelVersion.getVersion()
        ));
    }

    public void setupFirstAdmin(Context ctx) {
        if (!authService.isSetupRequired()) {
            ctx.status(400).json(Map.of("error", "Setup already completed"));
            return;
        }

        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String username = body.path("username").asText(null);
        String password = body.path("password").asText(null);
        String displayName = body.path("displayName").asText(null);
        String setupKey = body.path("setupKey").asText("").trim();

        if (username == null || password == null) {
            ctx.status(400).json(Map.of("error", "Username and password are required"));
            return;
        }

        String expectedKey = br.com.craftpanel.CraftPanelServer.getInitialSetupKey();
        if (expectedKey != null && !expectedKey.equalsIgnoreCase(setupKey)) {
            ctx.status(400).json(Map.of("error", "Invalid console security key. Check your server console logs for the setup key."));
            return;
        }

        try {
            User user = authService.setupFirstAdmin(username, password, displayName);
            br.com.craftpanel.CraftPanelServer.clearInitialSetupKey();
            Session session = authService.login(username, password, ctx.ip(), ctx.userAgent());

            Cookie cookie = new Cookie("craftpanel_session", session.token());
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setSecure(config.security().requireSecureCookies());
            cookie.setSameSite(SameSite.LAX);
            ctx.cookie(cookie);

            auditService.log(user.id(), user.username(), "SETUP_INITIAL_ADMIN", "USER", user.id(), "Created initial administrator account", ctx.ip(), "SUCCESS");

            ctx.json(Map.of(
                    "success", true,
                    "user", user,
                    "token", session.token()
            ));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Failed to setup initial administrator"));
        }
    }
}
