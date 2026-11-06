package br.com.craftpanel.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRouterTest {
    private static final String MODELS = """
            {"data":[
              {"id":"acme/paid","name":"Acme Paid","supported_parameters":["tools"],"architecture":{"output_modalities":["text"]}},
              {"id":"acme/no-tools","name":"Acme No Tools","supported_parameters":["temperature"],"architecture":{"output_modalities":["text"]}},
              {"id":"acme/image","name":"Acme Image","supported_parameters":["tools"],"architecture":{"output_modalities":["image"]}},
              {"id":"acme/small:free","name":"Acme Small (free)","supported_parameters":["tools"],"architecture":{"output_modalities":["text"]}},
              {"id":"openrouter/free","name":"Free Models Router","supported_parameters":["tools"],"architecture":{"output_modalities":["text"]}}
            ]}""";

    private HttpServer server;
    private final AtomicReference<String> chatBody = new AtomicReference<>();
    private final AtomicReference<String> chatAuth = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/key", exchange -> {
            boolean good = "Bearer good".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, good ? 200 : 401, "application/json", good ? "{\"data\":{}}" : "{\"error\":{\"message\":\"No auth credentials found\",\"code\":401}}");
        });
        server.createContext("/models", exchange -> reply(exchange, 200, "application/json", MODELS));
        server.createContext("/chat/completions", exchange -> {
            chatAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            chatBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 200, "text/event-stream", """
                    data: {"id":"1","model":"acme/small:free","choices":[{"index":0,"delta":{"role":"assistant","content":"Ol"}}]}

                    data: {"id":"1","model":"acme/small:free","choices":[{"index":0,"delta":{"content":"á"},"finish_reason":"stop"}]}

                    data: [DONE]

                    """);
        });
        server.start();
        System.setProperty("craftpanel.openrouter.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
        System.clearProperty("craftpanel.openrouter.baseUrl");
    }

    private static void reply(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void listsOnlyToolCapableTextModelsWithTheFreeRouterFirst() throws Exception {
        List<AiModel> models = OpenRouterModels.list("good");
        assertEquals(List.of("openrouter/free", "acme/small:free", "acme/paid"), models.stream().map(AiModel::id).toList());
        assertEquals("openrouter/free", OpenRouterModels.preferred(models).orElseThrow().id());
    }

    @Test
    void rejectsABadKeyEvenThoughTheModelListIsPublic() {
        IOException error = assertThrows(IOException.class, () -> OpenRouterModels.list("bad"));
        assertTrue(error.getMessage().contains("No auth credentials"));
    }

    @Test
    void streamsTheChatThroughTheChosenModelWithTools() throws Exception {
        AiSettings.AiConfig config = new AiSettings.AiConfig(AiProvider.OPENROUTER, "good", OpenRouterModels.FREE_ROUTER, false);
        ToolSpecification tool = ToolSpecification.builder().name("read_console").description("Reads the console").build();
        ChatRequest request = ChatRequest.builder().messages(UserMessage.from("Oi")).toolSpecifications(tool).build();
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();

        AiChatHandler.buildModel(config).chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partial) {
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                done.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });

        assertEquals("Olá", done.get(10, TimeUnit.SECONDS).aiMessage().text());
        assertEquals("Bearer good", chatAuth.get());
        assertTrue(chatBody.get().replaceAll("\\s", "").contains("\"model\":\"openrouter/free\""), chatBody.get());
        assertTrue(chatBody.get().contains("read_console"), chatBody.get());
    }
}
