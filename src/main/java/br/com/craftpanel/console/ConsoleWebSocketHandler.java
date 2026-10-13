package br.com.craftpanel.console;

import br.com.craftpanel.auth.AuthService;
import br.com.craftpanel.auth.Session;
import br.com.craftpanel.processes.ConsoleLine;
import br.com.craftpanel.processes.ProcessManager;
import br.com.craftpanel.processes.ProcessState;
import br.com.craftpanel.processes.ServerProcess;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConsoleWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final AuthService authService;
    private final UserService userService;
    private final ServerService serverService;
    private final ProcessManager processManager;

    private final Map<WsContext, ClientContext> clients = new ConcurrentHashMap<>();

    private record ClientContext(User user, String serverId, ServerProcess.Listener listener) {}

    public ConsoleWebSocketHandler(
            AuthService authService,
            UserService userService,
            ServerService serverService,
            ProcessManager processManager
    ) {
        this.authService = authService;
        this.userService = userService;
        this.serverService = serverService;
        this.processManager = processManager;
    }

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            String rawServerId = ctx.pathParam("serverId");
            String serverId = (rawServerId == null || rawServerId.isBlank() || "undefined".equalsIgnoreCase(rawServerId) || "null".equalsIgnoreCase(rawServerId)) ? "local" : rawServerId;
            User user = authenticate(ctx);

            if (user == null) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Unauthorized")));
                ctx.closeSession();
                return;
            }

            if (!userService.hasServerPermission(user, serverId, Permission.CONSOLE_VIEW) && !userService.hasServerPermission(user, "local", Permission.CONSOLE_VIEW)) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Permission denied: console.view")));
                ctx.closeSession();
                return;
            }

            Optional<MinecraftServer> serverOpt = serverService.getServer(serverId);
            if (serverOpt.isEmpty()) {
                serverOpt = serverService.getServer("local");
            }
            if (serverOpt.isEmpty()) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Server not found")));
                ctx.closeSession();
                return;
            }

            ServerProcess proc = processManager.getProcess(serverOpt.get());

            ServerProcess.Listener listener = new ServerProcess.Listener() {
                @Override
                public void onLine(ConsoleLine line) {
                    try {
                        ctx.send(MAPPER.writeValueAsString(Map.of(
                                "type", "line",
                                "line", line.line(),
                                "level", line.level(),
                                "timestamp", line.timestamp().toString()
                        )));
                    } catch (Exception ignored) {}
                }

                @Override
                public void onStateChanged(ProcessState newState) {
                    try {
                        ctx.send(MAPPER.writeValueAsString(Map.of(
                                "type", "state",
                                "state", newState.name()
                        )));
                    } catch (Exception ignored) {}
                }
            };

            proc.addListener(listener);
            clients.put(ctx, new ClientContext(user, serverId, listener));

            List<ConsoleLine> backlog = proc.getBuffer().getLines();
            ctx.send(MAPPER.writeValueAsString(Map.of(
                    "type", "backlog",
                    "lines", backlog,
                    "state", proc.getState().name()
            )));
        });

        ws.onMessage(ctx -> {
            ClientContext client = clients.get(ctx);
            if (client == null) return;

            if (!userService.hasServerPermission(client.user(), client.serverId(), Permission.CONSOLE_EXECUTE)) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Permission denied: console.execute")));
                return;
            }

            String msg = ctx.message();
            String command = msg;
            try {
                var node = MAPPER.readTree(msg);
                if (node.has("command")) {
                    command = node.get("command").asText();
                }
            } catch (Exception ignored) {}

            if (command != null && !command.isBlank()) {
                ServerProcess proc = processManager.getProcess(client.serverId());
                if (proc == null) {
                    proc = processManager.getProcess("local");
                }
                if (proc != null) {
                    try {
                        proc.sendCommand(command.trim());
                    } catch (Exception e) {
                        ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", e.getMessage())));
                    }
                }
            }
        });

        ws.onClose(ctx -> {
            ClientContext client = clients.remove(ctx);
            if (client != null) {
                ServerProcess proc = processManager.getProcess(client.serverId());
                if (proc != null) {
                    proc.removeListener(client.listener());
                }
            }
        });

        ws.onError(ctx -> {
            ClientContext client = clients.remove(ctx);
            if (client != null) {
                ServerProcess proc = processManager.getProcess(client.serverId());
                if (proc != null) {
                    proc.removeListener(client.listener());
                }
            }
        });
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
