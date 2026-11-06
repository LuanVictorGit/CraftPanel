package br.com.craftpanel.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Asks OpenRouter which models can drive the chat. Its model list is public, so the key is checked separately. */
public final class OpenRouterModels {
    /** Routes each request to a free model that supports what the request needs, tool calling included. */
    public static final String FREE_ROUTER = "openrouter/free";

    private static final ObjectMapper JSON = new ObjectMapper();

    private OpenRouterModels() {}

    /** The official endpoint; the system property only exists to point tests at a local stub. */
    public static String baseUrl() {
        return System.getProperty("craftpanel.openrouter.baseUrl", "https://openrouter.ai/api/v1");
    }

    public static List<AiModel> list(String apiKey) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        get(client, "/key", apiKey);

        List<AiModel> models = new ArrayList<>();
        for (JsonNode node : get(client, "/models", apiKey).path("data")) {
            if (supportsChat(node)) {
                String id = node.path("id").asText();
                // LangChain4j reads reasoning from a field OpenRouter does not stream, so the chat cannot show it.
                models.add(new AiModel(id, node.path("name").asText(id), false));
            }
        }
        models.sort(Comparator.comparingInt(OpenRouterModels::rank).thenComparing(AiModel::displayName, String.CASE_INSENSITIVE_ORDER));
        return models;
    }

    /** Starts with what costs nothing: the chat would otherwise spend credits on a model nobody picked. */
    public static Optional<AiModel> preferred(List<AiModel> models) {
        return models.stream().min(Comparator.comparingInt(OpenRouterModels::rank));
    }

    private static int rank(AiModel model) {
        if (model.id().equals(FREE_ROUTER)) {
            return 0;
        }
        return model.id().endsWith(":free") ? 1 : 2;
    }

    private static JsonNode get(HttpClient client, String path, String apiKey) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = JSON.readTree(response.body());
        if (response.statusCode() != 200) {
            throw new IOException(body.path("error").path("message").asText("OpenRouter returned HTTP " + response.statusCode()));
        }
        return body;
    }

    /** The assistant works through tools, so a model without them could only talk. */
    private static boolean supportsChat(JsonNode node) {
        boolean tools = false;
        for (JsonNode parameter : node.path("supported_parameters")) {
            tools |= "tools".equals(parameter.asText());
        }
        boolean text = false;
        for (JsonNode modality : node.path("architecture").path("output_modalities")) {
            text |= "text".equals(modality.asText());
        }
        return tools && text;
    }
}
