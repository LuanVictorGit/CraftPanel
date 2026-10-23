package br.com.craftpanel.console;

import br.com.craftpanel.auth.AuthService;
import br.com.craftpanel.auth.Session;
import br.com.craftpanel.tasks.Task;
import br.com.craftpanel.tasks.TaskManager;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class EventsWebSocketHandler implements TaskManager.TaskListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventsWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private record Client(String userId, String sessionToken) {}

    private final AuthService authService;
    private final UserService userService;
    private final Map<WsContext, Client> clients = new ConcurrentHashMap<>();

    public EventsWebSocketHandler(AuthService authService, UserService userService, TaskManager taskManager) {
        this.authService = authService;
        this.userService = userService;
        taskManager.addListener(this);
    }

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            Client client = authenticate(ctx);
            if (client == null) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Unauthorized")));
                ctx.closeSession();
                return;
            }
            clients.put(ctx, client);
            ctx.send(MAPPER.writeValueAsString(Map.of("type", "connected")));
        });

        ws.onClose(clients::remove);
        ws.onError(clients::remove);
    }

    public void broadcast(String type, Object payload) {
        try {
            String json = MAPPER.writeValueAsString(Map.of("type", type, "payload", payload));
            for (WsContext ctx : clients.keySet()) {
                try {
                    ctx.send(json);
                } catch (Exception ignored) {
                    clients.remove(ctx);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to broadcast event", e);
        }
    }

    /**
     * Delivers the event only to clients whose session is still valid and whose user currently holds
     * {@code permission} on {@code serverId}. Clients whose session is gone are disconnected.
     */
    public void broadcast(String type, Object payload, String serverId, String permission) {
        try {
            String json = MAPPER.writeValueAsString(Map.of("type", type, "payload", payload));
            for (Map.Entry<WsContext, Client> entry : clients.entrySet()) {
                WsContext ctx = entry.getKey();
                User user = currentUser(entry.getValue());
                if (user == null) {
                    clients.remove(ctx);
                    try {
                        ctx.closeSession();
                    } catch (Exception ignored) {}
                    continue;
                }
                if (!userService.hasServerPermission(user, serverId, permission)) {
                    continue;
                }
                try {
                    ctx.send(json);
                } catch (Exception ignored) {
                    clients.remove(ctx);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to broadcast event", e);
        }
    }

    @Override
    public void onTaskUpdated(Task task) {
        broadcast("task_update", task);
    }

    private User currentUser(Client client) {
        if (client.sessionToken() == null) {
            return null;
        }
        return authService.validateSession(client.sessionToken())
                .filter(session -> session.userId().equals(client.userId()))
                .flatMap(session -> userService.getUser(session.userId()))
                .orElse(null);
    }

    private Client authenticate(WsContext ctx) {
        String ticket = ctx.queryParam("ticket");
        if (ticket != null && !ticket.isBlank()) {
            Optional<Session> session = authService.redeemWebSocketTicket(ticket);
            if (session.isPresent() && userService.getUser(session.get().userId()).isPresent()) {
                return new Client(session.get().userId(), session.get().token());
            }
        }

        String cookieToken = ctx.cookie("craftpanel_session");
        if (cookieToken != null && !cookieToken.isBlank()) {
            Optional<Session> session = authService.validateSession(cookieToken);
            if (session.isPresent() && userService.getUser(session.get().userId()).isPresent()) {
                return new Client(session.get().userId(), cookieToken);
            }
        }

        return null;
    }
}
