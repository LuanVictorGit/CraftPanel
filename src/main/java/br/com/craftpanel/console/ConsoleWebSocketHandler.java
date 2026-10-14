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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
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

            if (!userService.hasServerPermission(user, serverId, Permission.CONSOLE_VIEW)) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Permission denied: console.view")));
                ctx.closeSession();
                return;
            }

            ServerProcess proc = processManager.getProcess(serverId);
            if (proc == null) {
                Optional<MinecraftServer> serverOpt = serverService.getServer(serverId);
                if (serverOpt.isPresent()) {
                    proc = processManager.getProcess(serverOpt.get());
                } else if ("local".equalsIgnoreCase(serverId)) {
                    serverOpt = serverService.listServers().stream().findFirst();
                    if (serverOpt.isPresent()) {
                        proc = processManager.getProcess(serverOpt.get());
                    }
                }
            }

            if (proc == null) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Server process unavailable for " + serverId)));
                ctx.closeSession();
                return;
            }

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

            String msg = ctx.message();
            String command = msg;
            try {
                var node = MAPPER.readTree(msg);
                String msgType = node.path("type").asText("");
                if ("tab_complete".equalsIgnoreCase(msgType) || node.has("buffer")) {
                    String buffer = node.path("buffer").asText("");
                    List<String> completions = resolveTabCompletions(buffer);
                    Map<String, String> hint = resolveCommandHint(buffer);
                    ctx.send(MAPPER.writeValueAsString(Map.of(
                            "type", "tab_complete",
                            "buffer", buffer,
                            "completions", completions,
                            "hint", hint
                    )));
                    return;
                }

                if (node.has("command")) {
                    command = node.get("command").asText();
                }
            } catch (Exception ignored) {}

            if (!userService.hasServerPermission(client.user(), client.serverId(), Permission.CONSOLE_EXECUTE)) {
                ctx.send(MAPPER.writeValueAsString(Map.of("type", "error", "message", "Permission denied: console.execute")));
                return;
            }

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

    private static volatile Class<?> bukkitClass = null;
    private static volatile Method getServerMethod = null;
    private static volatile Method getConsoleSenderMethod = null;
    private static volatile Method tabCompleteMethod = null;
    private static volatile Method getCommandMapMethod = null;
    private static volatile Method getKnownCommandsMethod = null;
    private static volatile Field knownCommandsField = null;
    private static volatile Method getCommandMethod = null;
    private static volatile boolean reflectionInitialized = false;

    private static synchronized void ensureReflectionInit() {
        if (reflectionInitialized) return;
        try {
            bukkitClass = Class.forName("org.bukkit.Bukkit");
            getServerMethod = bukkitClass.getMethod("getServer");
            getConsoleSenderMethod = bukkitClass.getMethod("getConsoleSender");
            Class<?> senderClass = Class.forName("org.bukkit.command.CommandSender");
            Object server = getServerMethod.invoke(null);
            if (server != null) {
                tabCompleteMethod = server.getClass().getMethod("tabComplete", senderClass, String.class);
                getCommandMapMethod = server.getClass().getMethod("getCommandMap");
                Object cmdMap = getCommandMapMethod.invoke(server);
                if (cmdMap != null) {
                    try {
                        getKnownCommandsMethod = cmdMap.getClass().getMethod("getKnownCommands");
                    } catch (Throwable ignored) {
                        Class<?> curr = cmdMap.getClass();
                        while (curr != null && curr != Object.class) {
                            try {
                                knownCommandsField = curr.getDeclaredField("knownCommands");
                                knownCommandsField.setAccessible(true);
                                break;
                            } catch (Throwable ignored2) {
                                curr = curr.getSuperclass();
                            }
                        }
                    }
                    try {
                        getCommandMethod = cmdMap.getClass().getMethod("getCommand", String.class);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        reflectionInitialized = true;
    }

    public static List<String> resolveTabCompletions(String buffer) {
        List<String> completions = new ArrayList<>();
        if (buffer == null) buffer = "";
        String query = buffer;

        ensureReflectionInit();

        if (tabCompleteMethod != null && getServerMethod != null && getConsoleSenderMethod != null) {
            try {
                Object server = getServerMethod.invoke(null);
                Object consoleSender = getConsoleSenderMethod.invoke(null);
                String cmd = query.startsWith("/") ? query : ("/" + query);
                List<?> results = (List<?>) tabCompleteMethod.invoke(server, consoleSender, cmd);
                if (results != null) {
                    for (Object r : results) {
                        if (r != null) {
                            String str = r.toString();
                            if (str.startsWith("/") && !query.startsWith("/")) {
                                str = str.substring(1);
                            }
                            completions.add(str);
                        }
                    }
                }

                if (completions.isEmpty() && !query.startsWith("/")) {
                    results = (List<?>) tabCompleteMethod.invoke(server, consoleSender, query);
                    if (results != null) {
                        for (Object r : results) {
                            if (r != null) completions.add(r.toString());
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (completions.isEmpty() && !query.contains(" ") && getCommandMapMethod != null && getServerMethod != null) {
            String clean = query.startsWith("/") ? query.substring(1).toLowerCase() : query.toLowerCase();
            try {
                Object server = getServerMethod.invoke(null);
                Object commandMap = getCommandMapMethod.invoke(server);
                Map<?, ?> map = null;
                if (getKnownCommandsMethod != null) {
                    map = (Map<?, ?>) getKnownCommandsMethod.invoke(commandMap);
                } else if (knownCommandsField != null) {
                    map = (Map<?, ?>) knownCommandsField.get(commandMap);
                }
                if (map != null) {
                    for (Object k : map.keySet()) {
                        String cmdName = String.valueOf(k);
                        if (!cmdName.contains(":") && cmdName.toLowerCase().startsWith(clean)) {
                            completions.add(query.startsWith("/") ? ("/" + cmdName) : cmdName);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        completions.sort(String.CASE_INSENSITIVE_ORDER);
        if (completions.size() > 50) {
            return completions.subList(0, 50);
        }
        return completions;
    }

    public static Map<String, String> resolveCommandHint(String buffer) {
        if (buffer == null || buffer.isBlank()) {
            return Map.of();
        }
        String trimmed = buffer.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        String firstWord = trimmed.split(" ")[0].toLowerCase();
        ensureReflectionInit();
        if (getServerMethod != null && getCommandMapMethod != null && getCommandMethod != null) {
            try {
                Object server = getServerMethod.invoke(null);
                Object commandMap = getCommandMapMethod.invoke(server);
                Object cmd = getCommandMethod.invoke(commandMap, firstWord);
                if (cmd != null) {
                    Method getUsage = cmd.getClass().getMethod("getUsage");
                    Method getDesc = cmd.getClass().getMethod("getDescription");
                    String usage = (String) getUsage.invoke(cmd);
                    String desc = (String) getDesc.invoke(cmd);
                    return Map.of(
                            "command", firstWord,
                            "usage", usage != null ? usage.replace("<command>", firstWord) : ("/" + firstWord),
                            "description", desc != null ? desc : ""
                    );
                }
            } catch (Throwable ignored) {}
        }
        return Map.of();
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
