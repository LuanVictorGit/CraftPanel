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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventsWebSocketHandler implements TaskManager.TaskListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventsWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final AuthService authService;
    private final UserService userService;
    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();

    public EventsWebSocketHandler(AuthService authService, UserService userService, TaskManager taskManager) {
        this.authService = authService;
        this.userService = userService;
        taskManager.addListener(this);
    }

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            User user = authenticate(ctx);
            if (user == null) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Unauthorized")));
                ctx.closeSession();
                return;
            }
            clients.add(ctx);
            ctx.send(MAPPER.writeValueAsString(Map.of("type", "connected")));
        });

        ws.onClose(clients::remove);
        ws.onError(clients::remove);
    }

    public void broadcast(String type, Object payload) {
        try {
            String json = MAPPER.writeValueAsString(Map.of("type", type, "payload", payload));
            for (WsContext ctx : clients) {
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

    private User authenticate(WsContext ctx) {
        String ticket = ctx.queryParam("ticket");
        if (ticket != null && !ticket.isBlank()) {
            Optional<String> userId = authService.validateWebSocketTicket(ticket);
            if (userId.isPresent()) {
                return userService.getUser(userId.get()).orElse(null);
            }
        }

        String cookieToken = ctx.cookie("craftpanel_session");
        if (cookieToken != null && !cookieToken.isBlank()) {
            Optional<Session> session = authService.validateSession(cookieToken);
            if (session.isPresent()) {
                return userService.getUser(session.get().userId()).orElse(null);
            }
        }

        return null;
    }
}
