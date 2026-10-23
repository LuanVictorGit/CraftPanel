package br.com.craftpanel.monitoring;

import br.com.craftpanel.auth.AuthService;
import br.com.craftpanel.auth.Session;
import br.com.craftpanel.console.WebSocketSender;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MonitoringWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(MonitoringWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final AuthService authService;
    private final UserService userService;
    private final MonitoringService monitoringService;
    private final ServerService serverService;

    private final Set<WsContext> clients = ConcurrentHashMap.newKeySet();
    private final Map<WsContext, String> clientServerMap = new ConcurrentHashMap<>();
    private final AtomicBoolean collectionFailureLogged = new AtomicBoolean();
    private final ScheduledExecutorService scheduler;

    public MonitoringWebSocketHandler(
            AuthService authService,
            UserService userService,
            MonitoringService monitoringService,
            ServerService serverService
    ) {
        this.authService = authService;
        this.userService = userService;
        this.monitoringService = monitoringService;
        this.serverService = serverService;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "craftpanel-spark-ws");
            t.setDaemon(true);
            return t;
        });

        this.scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            User user = authenticate(ctx);
            if (user == null) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Unauthorized")));
                ctx.closeSession();
                return;
            }

            String serverId = ctx.pathParamMap().getOrDefault("serverId", "local");
            clients.add(ctx);
            clientServerMap.put(ctx, serverId);

            WebSocketSender.send(ctx, MAPPER.writeValueAsString(Map.of("type", "connected", "serverId", serverId)));

            String telemetry = telemetryJson(serverId);
            if (telemetry != null) {
                WebSocketSender.send(ctx, telemetry);
            }
        });

        ws.onClose(this::forget);

        ws.onError(this::forget);

        ws.onMessage(ctx -> {
            try {
                String message = ctx.message();
                if (message.contains("ping")) {
                    WebSocketSender.send(ctx, MAPPER.writeValueAsString(Map.of("type", "pong", "time", System.currentTimeMillis())));
                }
            } catch (Exception ignored) {}
        });
    }

    private void tick() {
        if (clients.isEmpty()) {
            return;
        }

        Map<String, String> payloads = new HashMap<>();
        for (WsContext ctx : clients) {
            String serverId = clientServerMap.getOrDefault(ctx, "local");
            String json = payloads.computeIfAbsent(serverId, this::telemetryJson);
            if (json != null && !WebSocketSender.send(ctx, json)) {
                forget(ctx);
            }
        }
    }

    private String telemetryJson(String serverId) {
        try {
            Optional<MinecraftServer> serverOpt = serverService.getServer(serverId);
            if (serverOpt.isEmpty()) {
                return null;
            }

            SparkTelemetry telemetry = monitoringService.collectSparkTelemetry(serverOpt.get());
            String json = MAPPER.writeValueAsString(Map.of("type", "telemetry", "data", telemetry));
            collectionFailureLogged.set(false);
            return json;
        } catch (Exception e) {
            // Runs every second: log the first failure, not a stack trace per tick.
            if (collectionFailureLogged.compareAndSet(false, true)) {
                LOGGER.warn("Failed to collect spark telemetry: {}", e.toString());
            }
            return null;
        }
    }

    private void forget(WsContext ctx) {
        clients.remove(ctx);
        clientServerMap.remove(ctx);
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

    public void stop() {
        scheduler.shutdownNow();
    }
}
