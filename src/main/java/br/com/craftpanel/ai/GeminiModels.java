package br.com.craftpanel.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Asks the Gemini API which models a key can use, which also proves that the key works. */
public final class GeminiModels {
    private static final ObjectMapper JSON = new ObjectMapper();

    private GeminiModels() {}

    /** The official endpoint; the system property only exists to point tests at a local stub. */
    public static String baseUrl() {
        return System.getProperty("craftpanel.gemini.baseUrl", "https://generativelanguage.googleapis.com/v1beta");
    }

    public static List<AiModel> list(String apiKey) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        List<AiModel> models = new ArrayList<>();
        String pageToken = null;
        do {
            String url = baseUrl() + "/models?pageSize=200"
                    + (pageToken == null ? "" : "&pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("x-goog-api-key", apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = JSON.readTree(response.body());
            if (response.statusCode() != 200) {
                throw new IOException(body.path("error").path("message").asText("Gemini API returned HTTP " + response.statusCode()));
            }
            for (JsonNode node : body.path("models")) {
                if (supportsChat(node)) {
                    String id = node.path("name").asText().replaceFirst("^models/", "");
                    models.add(new AiModel(id, node.path("displayName").asText(id), node.path("thinking").asBoolean(false)));
                }
            }
            pageToken = body.path("nextPageToken").asText(null);
        } while (pageToken != null && !pageToken.isBlank());
        return models;
    }

    private static boolean supportsChat(JsonNode node) {
        boolean generates = false;
        for (JsonNode method : node.path("supportedGenerationMethods")) {
            generates |= "generateContent".equals(method.asText());
        }
        String id = node.path("name").asText().toLowerCase(Locale.ROOT);
        return generates && id.contains("gemini") && !id.contains("embedding") && !id.contains("image") && !id.contains("tts");
    }

    /** A sensible first choice when a key is saved: the rolling Flash alias, or else any Flash model. */
    public static Optional<AiModel> preferred(List<AiModel> models) {
        return models.stream().filter(m -> m.id().equals("gemini-flash-latest")).findFirst()
                .or(() -> models.stream().filter(m -> m.id().contains("flash") && !m.id().contains("lite")).findFirst())
                .or(() -> models.stream().findFirst());
    }
}
