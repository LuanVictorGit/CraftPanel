package br.com.craftpanel.plugins;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarMetadataExtractor {
    @SuppressWarnings("unchecked")
    public static InstalledPlugin extract(File jarFile) {
        String filename = jarFile.getName();
        boolean enabled = !filename.endsWith(".disabled");
        long sizeBytes = jarFile.length();

        String name = filename.replace(".jar", "").replace(".disabled", "");
        String version = "Unknown";
        String author = "Unknown";
        String description = "";
        String mainClass = "";
        String apiVersion = "";
        boolean foliaSupported = false;
        List<String> dependencies = Collections.emptyList();
        List<String> softDependencies = Collections.emptyList();

        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("paper-plugin.yml");
            if (entry != null) {
                foliaSupported = true;
            } else {
                entry = jar.getJarEntry("plugin.yml");
            }

            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
                    Yaml yaml = new Yaml();
                    Map<String, Object> data = yaml.load(in);
                    if (data != null) {
                        if (data.containsKey("name")) {
                            name = String.valueOf(data.get("name"));
                        }
                        if (data.containsKey("version")) {
                            version = String.valueOf(data.get("version"));
                        }
                        if (data.containsKey("description")) {
                            description = String.valueOf(data.get("description"));
                        }
                        if (data.containsKey("main")) {
                            mainClass = String.valueOf(data.get("main"));
                        }
                        if (data.containsKey("api-version")) {
                            apiVersion = String.valueOf(data.get("api-version"));
                        }
                        if (data.containsKey("folia-supported")) {
                            Object foliaVal = data.get("folia-supported");
                            if (foliaVal instanceof Boolean b) {
                                foliaSupported = b;
                            } else if (foliaVal instanceof String s) {
                                foliaSupported = Boolean.parseBoolean(s);
                            }
                        }

                        if (data.containsKey("author")) {
                            author = String.valueOf(data.get("author"));
                        } else if (data.containsKey("authors")) {
                            Object authorsObj = data.get("authors");
                            if (authorsObj instanceof List<?> list) {
                                author = String.join(", ", list.stream().map(String::valueOf).toList());
                            }
                        }

                        if (data.containsKey("depend")) {
                            dependencies = toStringList(data.get("depend"));
                        }
                        if (data.containsKey("softdepend")) {
                            softDependencies = toStringList(data.get("softdepend"));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return new InstalledPlugin(
                filename,
                name,
                version,
                author,
                description,
                mainClass,
                apiVersion,
                foliaSupported,
                enabled,
                false,
                false,
                sizeBytes,
                dependencies,
                softDependencies
        );
    }

    private static List<String> toStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (obj instanceof String s) {
            return List.of(s);
        }
        return Collections.emptyList();
    }
}
