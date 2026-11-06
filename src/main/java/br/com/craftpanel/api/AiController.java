package br.com.craftpanel.api;

import br.com.craftpanel.ai.AiSettings;
import br.com.craftpanel.ai.ConversationStore;
import br.com.craftpanel.ai.GeminiModels;
import br.com.craftpanel.audit.AuditService;
import br.com.craftpanel.mcp.McpEndpoint;
import br.com.craftpanel.mcp.McpTokenService;
import br.com.craftpanel.users.Permission;
import br.com.craftpanel.users.User;
import br.com.craftpanel.users.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AiController {
    private static final int TOOL_PREVIEW_CHARS = 4_000;

    private final AiSettings settings;
    private final ConversationStore conversations;
    private final McpTokenService mcpTokens;
    private final UserService userService;
    private final AuditService auditService;
    private final AuthMiddleware authMiddleware;

    public AiController(AiSettings settings, ConversationStore conversations, McpTokenService mcpTokens,
                        UserService userService, AuditService auditService, AuthMiddleware authMiddleware) {
        this.settings = settings;
        this.conversations = conversations;
        this.mcpTokens = mcpTokens;
        this.userService = userService;
        this.auditService = auditService;
        this.authMiddleware = authMiddleware;
    }

    private void requireFeature(Context ctx, String permission) {
        if (!userService.hasFeaturePermission(AuthMiddleware.requireUser(ctx), permission)) {
            throw new ForbiddenResponse("Permission denied: " + permission);
        }
    }

    public void status(Context ctx) {
        User user = AuthMiddleware.requireUser(ctx);
        Map<String, Object> status = new HashMap<>();
        Optional<AiSettings.GeminiConfig> gemini = settings.gemini();
        status.put("configured", gemini.isPresent());
        status.put("model", gemini.map(AiSettings.GeminiConfig::model).orElse(null));
        status.put("thinking", gemini.map(AiSettings.GeminiConfig::thinking).orElse(false));
        status.put("canChat", userService.hasFeaturePermission(user, Permission.AI_CHAT));
        status.put("canManage", userService.hasPermission(user, Permission.SETTINGS_MANAGE));
        status.put("canUseMcp", userService.hasFeaturePermission(user, Permission.MCP_TOKENS));
        ctx.json(status);
    }

    public void models(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.SETTINGS_MANAGE);
        Optional<String> key = settings.apiKey();
        if (key.isEmpty()) {
            ctx.json(List.of());
            return;
        }
        try {
            ctx.json(GeminiModels.list(key.get()));
        } catch (Exception e) {
            ctx.status(502).json(Map.of("error", "Could not list Gemini models: " + e.getMessage()));
        }
    }

    public void saveSettings(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.SETTINGS_MANAGE);
        User user = AuthMiddleware.requireUser(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);

        if (body.path("clear").asBoolean(false)) {
            settings.clear();
            auditService.log(user.id(), user.username(), "AI_SETTINGS", "SETTINGS", "gemini", "Removed the Gemini API key", ctx.ip(), "SUCCESS");
            ctx.json(Map.of("success", true));
            return;
        }

        String newKey = body.path("apiKey").asText("").trim();
        String key = newKey.isEmpty() ? settings.apiKey().orElse(null) : newKey;
        if (key == null) {
            ctx.status(400).json(Map.of("error", "Enter a Gemini API key"));
            return;
        }

        List<GeminiModels.Model> models;
        try {
            models = GeminiModels.list(key);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Gemini did not accept this key: " + e.getMessage()));
            return;
        }

        String requested = body.path("model").asText("").trim();
        String wanted = requested.isEmpty() ? settings.model().orElse("") : requested;
        Optional<GeminiModels.Model> chosen = models.stream().filter(m -> m.id().equals(wanted)).findFirst()
                .or(() -> GeminiModels.preferred(models));
        if (chosen.isEmpty()) {
            ctx.status(400).json(Map.of("error", "This key has no Gemini chat models available"));
            return;
        }

        settings.saveApiKey(key);
        settings.saveModel(chosen.get().id(), chosen.get().thinking());
        auditService.log(user.id(), user.username(), "AI_SETTINGS", "SETTINGS", "gemini", "Gemini model set to " + chosen.get().id(), ctx.ip(), "SUCCESS");
        ctx.json(Map.of("success", true, "model", chosen.get().id(), "thinking", chosen.get().thinking()));
    }

    public void listConversations(Context ctx) {
        requireFeature(ctx, Permission.AI_CHAT);
        ctx.json(conversations.list(AuthMiddleware.requireUser(ctx).id()));
    }

    public void getConversation(Context ctx) {
        requireFeature(ctx, Permission.AI_CHAT);
        User user = AuthMiddleware.requireUser(ctx);
        String id = ctx.pathParam("id");
        ConversationStore.Conversation conversation = conversations.find(user.id(), id)
                .orElseThrow(() -> new NotFoundResponse("Conversation not found"));
        ctx.json(Map.of("conversation", conversation, "items", toItems(conversations.getMessages(id))));
    }

    public void deleteConversation(Context ctx) {
        requireFeature(ctx, Permission.AI_CHAT);
        if (!conversations.delete(AuthMiddleware.requireUser(ctx).id(), ctx.pathParam("id"))) {
            throw new NotFoundResponse("Conversation not found");
        }
        ctx.json(Map.of("success", true));
    }

    // The browser draws a saved conversation from the same kinds of blocks it receives live.
    static List<Map<String, Object>> toItems(List<ChatMessage> messages) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage user) {
                items.add(Map.of("kind", "user", "text", user.hasSingleText() ? user.singleText() : ""));
            } else if (message instanceof AiMessage ai) {
                if (ai.thinking() != null && !ai.thinking().isBlank()) {
                    items.add(Map.of("kind", "thinking", "text", ai.thinking()));
                }
                if (ai.text() != null && !ai.text().isBlank()) {
                    items.add(Map.of("kind", "assistant", "text", ai.text()));
                }
                if (ai.hasToolExecutionRequests()) {
                    for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
                        Map<String, Object> call = new LinkedHashMap<>();
                        call.put("kind", "tool_call");
                        call.put("id", String.valueOf(request.id()));
                        call.put("name", request.name());
                        call.put("args", String.valueOf(request.arguments()));
                        items.add(call);
                    }
                }
            } else if (message instanceof ToolExecutionResultMessage result) {
                String text = String.valueOf(result.text());
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("kind", "tool_result");
                call.put("id", String.valueOf(result.id()));
                call.put("name", result.toolName());
                call.put("output", text.length() > TOOL_PREVIEW_CHARS ? text.substring(0, TOOL_PREVIEW_CHARS) + "\n..." : text);
                call.put("ok", !Boolean.TRUE.equals(result.isError()));
                items.add(call);
            }
        }
        return items;
    }

    public void listMcpTokens(Context ctx) {
        requireFeature(ctx, Permission.MCP_TOKENS);
        ctx.json(Map.of("tokens", mcpTokens.list(AuthMiddleware.requireUser(ctx).id()), "path", McpEndpoint.PATH));
    }

    public void createMcpToken(Context ctx) {
        requireFeature(ctx, Permission.MCP_TOKENS);
        User user = AuthMiddleware.requireUser(ctx);
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String name = body.path("name").asText("").trim();
        if (name.isEmpty() || name.length() > 60) {
            ctx.status(400).json(Map.of("error", "The token name must have between 1 and 60 characters"));
            return;
        }
        boolean readOnly = body.path("readOnly").asBoolean(false);
        McpTokenService.CreatedToken created = mcpTokens.create(user.id(), name, readOnly);
        auditService.log(user.id(), user.username(), "MCP_TOKEN_CREATE", "MCP_TOKEN", created.info().id(),
                "Created MCP token \"" + name + "\"" + (readOnly ? " (read-only)" : ""), ctx.ip(), "SUCCESS");
        ctx.json(created);
    }

    public void revokeMcpToken(Context ctx) {
        requireFeature(ctx, Permission.MCP_TOKENS);
        User user = AuthMiddleware.requireUser(ctx);
        String id = ctx.pathParam("id");
        if (!mcpTokens.revoke(user.id(), id)) {
            throw new NotFoundResponse("Token not found");
        }
        auditService.log(user.id(), user.username(), "MCP_TOKEN_REVOKE", "MCP_TOKEN", id, "Revoked MCP token", ctx.ip(), "SUCCESS");
        ctx.json(Map.of("success", true));
    }
}
