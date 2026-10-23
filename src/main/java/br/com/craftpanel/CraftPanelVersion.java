package br.com.craftpanel;

import java.io.InputStream;
import java.util.Map;

public final class CraftPanelVersion {
    private static volatile String cachedVersion = null;

    private CraftPanelVersion() {}

    public static String getVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }

        synchronized (CraftPanelVersion.class) {
            if (cachedVersion != null) {
                return cachedVersion;
            }

            try {
                CraftPanelPlugin plugin = CraftPanelPlugin.getInstance();
                if (plugin != null && plugin.getDescription() != null) {
                    String v = plugin.getDescription().getVersion();
                    if (v != null && !v.isBlank()) {
                        cachedVersion = v.trim();
                        return cachedVersion;
                    }
                }
            } catch (Throwable ignored) {}

            try (InputStream in = CraftPanelVersion.class.getResourceAsStream("/plugin.yml")) {
                if (in != null) {
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    Map<String, Object> map = yaml.load(in);
                    if (map != null && map.get("version") != null) {
                        cachedVersion = String.valueOf(map.get("version")).trim();
                        return cachedVersion;
                    }
                }
            } catch (Throwable ignored) {}

            cachedVersion = "1.2.7";
            return cachedVersion;
        }
    }
}
