package br.com.craftpanel.installer;

import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class ServerInstallerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerInstallerService.class);
    private final ServerService serverService;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public record InstallParams(
            String name,
            String description,
            String software,
            String minecraftVersion,
            String buildNumber,
            int minMemoryMb,
            int maxMemoryMb,
            int serverPort,
            String jvmArgs,
            boolean acceptEula
    ) {}

    public ServerInstallerService(ServerService serverService) {
        this.serverService = serverService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
    }

    public List<String> getAvailableVersions(String software) {
        String soft = software.toLowerCase().trim();
        try {
            if ("paper".equals(soft) || "folia".equals(soft)) {
                String url = "https://api.papermc.io/v2/projects/" + soft;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode node = mapper.readTree(resp.body()).path("versions");
                    List<String> versions = new ArrayList<>();
                    if (node.isArray()) {
                        for (JsonNode v : node) versions.add(v.asText());
                    }
                    Collections.reverse(versions);
                    return versions;
                }
            } else if ("purpur".equals(soft)) {
                String url = "https://api.purpurmc.org/v2/purpur";
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode node = mapper.readTree(resp.body()).path("versions");
                    List<String> versions = new ArrayList<>();
                    if (node.isArray()) {
                        for (JsonNode v : node) versions.add(v.asText());
                    }
                    Collections.reverse(versions);
                    return versions;
                }
            } else if ("vanilla".equals(soft) || "spigot".equals(soft)) {
                String url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode node = mapper.readTree(resp.body()).path("versions");
                    List<String> versions = new ArrayList<>();
                    if (node.isArray()) {
                        for (JsonNode v : node) {
                            if ("release".equalsIgnoreCase(v.path("type").asText())) {
                                versions.add(v.path("id").asText());
                            }
                        }
                    }
                    return versions;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to query versions for {}", software, e);
        }

        return List.of("1.21.1", "1.20.4", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.8");
    }

    public List<String> getAvailableBuilds(String software, String version) {
        String soft = software.toLowerCase().trim();
        try {
            if ("paper".equals(soft) || "folia".equals(soft)) {
                String url = "https://api.papermc.io/v2/projects/" + soft + "/versions/" + version;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode node = mapper.readTree(resp.body()).path("builds");
                    List<String> builds = new ArrayList<>();
                    if (node.isArray()) {
                        for (JsonNode b : node) builds.add(b.asText());
                    }
                    Collections.reverse(builds);
                    return builds;
                }
            } else if ("purpur".equals(soft)) {
                String url = "https://api.purpurmc.org/v2/purpur/" + version;
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode node = mapper.readTree(resp.body()).path("builds").path("all");
                    List<String> builds = new ArrayList<>();
                    if (node.isArray()) {
                        for (JsonNode b : node) builds.add(b.asText());
                    }
                    Collections.reverse(builds);
                    return builds;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to query builds for {} {}", software, version, e);
        }
        return List.of("latest");
    }

    public MinecraftServer installServer(InstallParams params, Consumer<Integer> progressConsumer, Consumer<String> logConsumer) throws Exception {
        logConsumer.accept("Iniciando instalação da instância: " + params.name());
        progressConsumer.accept(10);

        MinecraftServer server = serverService.createServer(
                params.name(),
                params.description(),
                params.software(),
                params.minecraftVersion(),
                null,
                null,
                "java",
                params.minMemoryMb(),
                params.maxMemoryMb(),
                params.serverPort(),
                params.jvmArgs(),
                false
        );

        Path serverDir = Paths.get(server.serverDir());
        Path jarDestination = Paths.get(server.jarPath());

        logConsumer.accept("Diretório da instância configurado em: " + serverDir);
        progressConsumer.accept(25);

        String downloadUrl = resolveDownloadUrl(params.software(), params.minecraftVersion(), params.buildNumber());
        logConsumer.accept("Baixando executável do servidor: " + downloadUrl);

        downloadArtifact(downloadUrl, jarDestination, pct -> {
            int totalPct = 25 + (int) (pct * 0.55);
            progressConsumer.accept(totalPct);
        });

        logConsumer.accept("Download do JAR concluído com sucesso.");
        progressConsumer.accept(85);

        if (params.acceptEula()) {
            Path eulaPath = serverDir.resolve("eula.txt");
            Files.writeString(eulaPath, "eula=true\n", StandardCharsets.UTF_8);
            logConsumer.accept("Contrato de licença (EULA) aceito automaticamente (eula.txt).");
        }

        Path propPath = serverDir.resolve("server.properties");
        if (!Files.exists(propPath)) {
            String initialProps = "server-port=" + params.serverPort() + "\n"
                    + "motd=CraftPanel Managed Server - " + params.name() + "\n"
                    + "max-players=20\n"
                    + "online-mode=true\n";
            Files.writeString(propPath, initialProps, StandardCharsets.UTF_8);
            logConsumer.accept("Arquivo server.properties gerado com porta " + params.serverPort());
        }

        Path pluginsDir = serverDir.resolve("plugins");
        if (!Files.exists(pluginsDir)) {
            Files.createDirectories(pluginsDir);
        }

        progressConsumer.accept(100);
        logConsumer.accept("Instalação do servidor finalizada com sucesso!");
        return server;
    }

    private String resolveDownloadUrl(String software, String version, String build) throws Exception {
        String soft = software.toLowerCase().trim();
        if ("paper".equals(soft) || "folia".equals(soft)) {
            String resolvedBuild = build;
            if (build == null || "latest".equalsIgnoreCase(build) || build.isBlank()) {
                List<String> builds = getAvailableBuilds(soft, version);
                resolvedBuild = builds.isEmpty() ? "1" : builds.get(0);
            }

            String buildUrl = "https://api.papermc.io/v2/projects/" + soft + "/versions/" + version + "/builds/" + resolvedBuild;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(buildUrl)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode downloads = mapper.readTree(resp.body()).path("downloads").path("application");
                String fileName = downloads.path("name").asText();
                return "https://api.papermc.io/v2/projects/" + soft + "/versions/" + version + "/builds/" + resolvedBuild + "/downloads/" + fileName;
            }
        } else if ("purpur".equals(soft)) {
            String resolvedBuild = (build == null || "latest".equalsIgnoreCase(build) || build.isBlank()) ? "latest" : build;
            return "https://api.purpurmc.org/v2/purpur/" + version + "/" + resolvedBuild + "/download";
        } else if ("vanilla".equals(soft)) {
            String manifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(manifestUrl)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode versions = mapper.readTree(resp.body()).path("versions");
                for (JsonNode v : versions) {
                    if (version.equalsIgnoreCase(v.path("id").asText())) {
                        String versionMetaUrl = v.path("url").asText();
                        HttpRequest vReq = HttpRequest.newBuilder().uri(URI.create(versionMetaUrl)).GET().build();
                        HttpResponse<String> vResp = httpClient.send(vReq, HttpResponse.BodyHandlers.ofString());
                        if (vResp.statusCode() == 200) {
                            return mapper.readTree(vResp.body()).path("downloads").path("server").path("url").asText();
                        }
                    }
                }
            }
        }
        throw new IllegalArgumentException("Could not resolve download URL for " + software + " " + version);
    }

    private void downloadArtifact(String url, Path destination, Consumer<Integer> progressConsumer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "CraftPanel-Web/1.0 (contact: minebuilders.com.br/discord)")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to download JAR: HTTP " + response.statusCode());
        }

        long length = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Path tempFile = destination.resolveSibling(destination.getFileName().toString() + ".download");

        try (InputStream in = response.body();
             var out = Files.newOutputStream(tempFile)) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
                if (length > 0 && progressConsumer != null) {
                    progressConsumer.accept((int) ((total * 100) / length));
                }
            }
        }

        Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
