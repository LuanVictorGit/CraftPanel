package br.com.craftpanel.modrinth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ModrinthClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModrinthClient.class);
    private static final Duration API_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String userAgent;

    public ModrinthClient(String userAgent, String baseUrl) {
        this.userAgent = userAgent != null ? userAgent : "CraftPanel-Web/1.0 (contact: minebuilders.com.br/discord)";
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.modrinth.com/v2";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
    }

    public record SearchResult(
            List<ModrinthProject> hits,
            int totalHits,
            int offset,
            int limit
    ) {}

    /**
     * Modrinth loaders whose plugins run on the given fork, e.g. a Paper server also runs Spigot and Bukkit
     * plugins. Folia only runs plugins that declare Folia support.
     */
    public static List<String> compatibleLoaders(String loaderOrSoftware) {
        String soft = loaderOrSoftware.toLowerCase().trim();
        if (soft.contains("folia")) return List.of("folia");
        if (soft.contains("purpur")) return List.of("purpur", "paper", "spigot", "bukkit");
        if (soft.contains("paper")) return List.of("paper", "spigot", "bukkit");
        if (soft.contains("spigot")) return List.of("spigot", "bukkit");
        if (soft.contains("bukkit")) return List.of("bukkit");
        return List.of(soft);
    }

    public SearchResult search(String query, String gameVersion, String loader, String index, int limit, int offset) throws Exception {
        StringBuilder url = new StringBuilder(baseUrl).append("/search?");
        List<String> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            params.add("query=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        }

        List<String> facets = new ArrayList<>();
        facets.add("[\"project_type:plugin\"]");

        if (loader != null && !loader.isBlank()) {
            facets.add(compatibleLoaders(loader).stream()
                    .map(l -> "\"categories:" + l + "\"")
                    .collect(Collectors.joining(",", "[", "]")));
        }
        if (gameVersion != null && !gameVersion.isBlank()) {
            facets.add("[\"versions:" + gameVersion.trim() + "\"]");
        }

        params.add("facets=" + URLEncoder.encode("[" + String.join(",", facets) + "]", StandardCharsets.UTF_8));
        params.add("limit=" + Math.min(Math.max(limit, 1), 50));
        params.add("offset=" + Math.max(offset, 0));

        if (index != null && !index.isBlank()) {
            params.add("index=" + URLEncoder.encode(index.trim(), StandardCharsets.UTF_8));
        }

        url.append(String.join("&", params));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("User-Agent", userAgent)
                .timeout(API_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Modrinth API error " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        int totalHits = root.path("total_hits").asInt();
        int respOffset = root.path("offset").asInt();
        int respLimit = root.path("limit").asInt();

        List<ModrinthProject> hits = new ArrayList<>();
        JsonNode hitsNode = root.path("hits");
        if (hitsNode.isArray()) {
            for (JsonNode h : hitsNode) {
                hits.add(mapProjectFromSearch(h));
            }
        }

        return new SearchResult(hits, totalHits, respOffset, respLimit);
    }

    public Optional<ModrinthProject> getProject(String idOrSlug) {
        try {
            String url = baseUrl + "/project/" + URLEncoder.encode(idOrSlug.trim(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .timeout(API_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Modrinth project error: " + response.statusCode());
            }

            JsonNode node = mapper.readTree(response.body());
            return Optional.of(mapProjectDetail(node));
        } catch (Exception e) {
            LOGGER.error("Failed to fetch project details for {}", idOrSlug, e);
            return Optional.empty();
        }
    }

    public List<ModrinthVersion> getProjectVersions(String idOrSlug, String gameVersion, String loader) throws Exception {
        StringBuilder url = new StringBuilder(baseUrl).append("/project/")
                .append(URLEncoder.encode(idOrSlug.trim(), StandardCharsets.UTF_8))
                .append("/version?");

        List<String> params = new ArrayList<>();
        if (loader != null && !loader.isBlank()) {
            String loaders = compatibleLoaders(loader).stream()
                    .map(l -> "\"" + l + "\"")
                    .collect(Collectors.joining(",", "[", "]"));
            params.add("loaders=" + URLEncoder.encode(loaders, StandardCharsets.UTF_8));
        }
        if (gameVersion != null && !gameVersion.isBlank()) {
            params.add("game_versions=" + URLEncoder.encode("[\"" + gameVersion.trim() + "\"]", StandardCharsets.UTF_8));
        }
        url.append(String.join("&", params));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("User-Agent", userAgent)
                .timeout(API_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to load versions for " + idOrSlug + ": " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        List<ModrinthVersion> list = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode vNode : root) {
                list.add(mapVersion(vNode));
            }
        }
        return list;
    }

    public void downloadFile(String downloadUrl, Path destination, Consumer<Integer> progressConsumer, String expectedHash) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", userAgent)
                .timeout(API_TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IllegalStateException("Download failed with HTTP " + response.statusCode());
        }

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Path tempFile = destination.resolveSibling(destination.getFileName().toString() + ".download");

        MessageDigest digest = expectedHash != null && expectedHash.trim().length() == 128
                ? MessageDigest.getInstance("SHA-512")
                : MessageDigest.getInstance("SHA-1");

        // HttpRequest.timeout() stops counting once the headers arrive, so a stalled body is cut off by closing it.
        InputStream body = response.body();
        AtomicBoolean timedOut = new AtomicBoolean();
        CompletableFuture<Void> watchdog = CompletableFuture.runAsync(() -> {
            timedOut.set(true);
            try {
                body.close();
            } catch (IOException ignored) {}
        }, CompletableFuture.delayedExecutor(DOWNLOAD_TIMEOUT.toSeconds(), TimeUnit.SECONDS));

        try {
            try (InputStream in = body;
                 var out = Files.newOutputStream(tempFile)) {
                byte[] buf = new byte[8192];
                long totalRead = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    digest.update(buf, 0, n);
                    totalRead += n;
                    if (contentLength > 0 && progressConsumer != null) {
                        int percent = (int) ((totalRead * 100) / contentLength);
                        progressConsumer.accept(percent);
                    }
                }
            }

            if (expectedHash != null && !expectedHash.isBlank()) {
                String calculated = HexFormat.of().formatHex(digest.digest());
                if (!calculated.equalsIgnoreCase(expectedHash.trim())) {
                    throw new SecurityException("Downloaded file hash mismatch: expected " + expectedHash + " but got " + calculated);
                }
            }

            Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (timedOut.get()) {
                throw new HttpTimeoutException("Download did not finish within " + DOWNLOAD_TIMEOUT.toMinutes() + " minutes");
            }
            throw e;
        } finally {
            watchdog.cancel(false);
            // A dropped connection or a hash mismatch must not leave a half-written file behind.
            Files.deleteIfExists(tempFile);
        }
    }

    private ModrinthProject mapProjectFromSearch(JsonNode node) {
        List<String> categories = new ArrayList<>();
        JsonNode cats = node.path("categories");
        if (cats.isArray()) {
            for (JsonNode c : cats) categories.add(c.asText());
        }

        return new ModrinthProject(
                node.path("project_id").asText(),
                node.path("slug").asText(),
                node.path("title").asText(),
                node.path("description").asText(),
                categories,
                node.path("client_side").asText(),
                node.path("server_side").asText(),
                node.path("icon_url").asText(),
                node.path("downloads").asInt(),
                node.path("follows").asInt(),
                node.path("date_modified").asText(),
                node.path("author").asText()
        );
    }

    private ModrinthProject mapProjectDetail(JsonNode node) {
        List<String> categories = new ArrayList<>();
        JsonNode cats = node.path("categories");
        if (cats.isArray()) {
            for (JsonNode c : cats) categories.add(c.asText());
        }

        return new ModrinthProject(
                node.path("id").asText(),
                node.path("slug").asText(),
                node.path("title").asText(),
                node.path("description").asText(),
                categories,
                node.path("client_side").asText(),
                node.path("server_side").asText(),
                node.path("icon_url").asText(),
                node.path("downloads").asInt(),
                node.path("followers").asInt(),
                node.path("updated").asText(),
                ""
        );
    }

    private ModrinthVersion mapVersion(JsonNode node) {
        List<String> gameVersions = new ArrayList<>();
        for (JsonNode gv : node.path("game_versions")) {
            gameVersions.add(gv.asText());
        }

        List<String> loaders = new ArrayList<>();
        for (JsonNode l : node.path("loaders")) {
            loaders.add(l.asText());
        }

        List<ModrinthVersion.VersionFile> files = new ArrayList<>();
        for (JsonNode f : node.path("files")) {
            Map<String, String> hashes = new HashMap<>();
            JsonNode hNode = f.path("hashes");
            if (hNode.isObject()) {
                hNode.fields().forEachRemaining(entry -> hashes.put(entry.getKey(), entry.getValue().asText()));
            }
            files.add(new ModrinthVersion.VersionFile(
                    f.path("url").asText(),
                    f.path("filename").asText(),
                    f.path("primary").asBoolean(),
                    f.path("size").asLong(),
                    hashes
            ));
        }

        List<ModrinthVersion.VersionDependency> deps = new ArrayList<>();
        for (JsonNode d : node.path("dependencies")) {
            deps.add(new ModrinthVersion.VersionDependency(
                    d.path("version_id").asText(),
                    d.path("project_id").asText(),
                    d.path("file_name").asText(),
                    d.path("dependency_type").asText()
            ));
        }

        return new ModrinthVersion(
                node.path("id").asText(),
                node.path("project_id").asText(),
                node.path("name").asText(),
                node.path("version_number").asText(),
                gameVersions,
                loaders,
                files,
                deps,
                node.path("changelog").asText(),
                node.path("date_published").asText()
        );
    }

    private List<String> cachedGameVersions = null;
    private long lastGameVersionsFetch = 0L;

    public synchronized List<String> getGameVersions() {
        long now = System.currentTimeMillis();
        if (cachedGameVersions != null && (now - lastGameVersionsFetch) < 3600000L) {
            return cachedGameVersions;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tag/game_version"))
                    .header("User-Agent", userAgent)
                    .timeout(API_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode array = mapper.readTree(response.body());
                List<String> list = new ArrayList<>();
                if (array.isArray()) {
                    for (int i = array.size() - 1; i >= 0; i--) {
                        JsonNode item = array.get(i);
                        if ("release".equalsIgnoreCase(item.path("version_type").asText())) {
                            String v = item.path("version").asText();
                            if (v != null && !v.isBlank() && !list.contains(v)) {
                                list.add(v);
                            }
                        }
                    }
                }
                if (!list.isEmpty()) {
                    cachedGameVersions = list;
                    lastGameVersionsFetch = now;
                    return list;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to fetch game versions from Modrinth: {}", e.getMessage());
        }

        return cachedGameVersions != null ? cachedGameVersions : List.of();
    }
}
