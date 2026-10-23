package br.com.craftpanel.modrinth;

import java.util.ArrayList;
import java.util.List;

public class CompatibilityChecker {
    public static CompatibilityResult check(String serverSoftware, String serverVersion, ModrinthVersion version) {
        // Maps names like "CraftBukkit" or "Spigot / Paper" onto the fork's primary Modrinth loader.
        String soft = serverSoftware != null ? ModrinthClient.compatibleLoaders(serverSoftware).get(0) : "";
        String mcVer = serverVersion != null ? serverVersion.trim() : "";

        List<String> loaders = version.loaders() != null ? version.loaders().stream().map(String::toLowerCase).toList() : List.of();
        List<String> gameVersions = version.gameVersions() != null ? version.gameVersions() : List.of();

        List<String> matchingLoaders = new ArrayList<>();
        List<String> matchingVersions = new ArrayList<>();

        boolean loaderMatch = false;
        if ("folia".equals(soft)) {
            if (loaders.contains("folia")) {
                loaderMatch = true;
                matchingLoaders.add("folia");
            }
        } else if ("purpur".equals(soft) && loaders.contains("purpur")) {
            loaderMatch = true;
            matchingLoaders.add("purpur");
        } else if ("paper".equals(soft) || "purpur".equals(soft)) {
            if (loaders.contains("paper")) {
                loaderMatch = true;
                matchingLoaders.add("paper");
            } else if (loaders.contains("spigot")) {
                loaderMatch = true;
                matchingLoaders.add("spigot");
            } else if (loaders.contains("bukkit")) {
                loaderMatch = true;
                matchingLoaders.add("bukkit");
            }
        } else if ("spigot".equals(soft)) {
            if (loaders.contains("spigot")) {
                loaderMatch = true;
                matchingLoaders.add("spigot");
            } else if (loaders.contains("bukkit")) {
                loaderMatch = true;
                matchingLoaders.add("bukkit");
            }
        } else if ("bukkit".equals(soft)) {
            if (loaders.contains("bukkit")) {
                loaderMatch = true;
                matchingLoaders.add("bukkit");
            }
        } else {
            if (loaders.contains(soft)) {
                loaderMatch = true;
                matchingLoaders.add(soft);
            }
        }

        boolean versionMatch = false;
        for (String gv : gameVersions) {
            if (gv.equalsIgnoreCase(mcVer)) {
                versionMatch = true;
                matchingVersions.add(gv);
                break;
            }
            if (isVersionFamily(gv, mcVer)) {
                versionMatch = true;
                matchingVersions.add(gv);
            }
        }

        if (version.dependencies() != null && !version.dependencies().isEmpty()) {
            boolean hasIncompatibleDep = version.dependencies().stream()
                    .anyMatch(d -> "incompatible".equalsIgnoreCase(d.dependencyType()));
            if (hasIncompatibleDep) {
                return new CompatibilityResult(
                        CompatibilityResult.Status.NEEDS_ANALYSIS,
                        "Possui dependências incompatíveis declaradas no Modrinth",
                        matchingVersions,
                        matchingLoaders
                );
            }
        }

        if (loaderMatch && versionMatch) {
            return new CompatibilityResult(
                    CompatibilityResult.Status.COMPATIBLE_CONFIRMED,
                    "Compatibilidade confirmada para " + serverSoftware + " " + serverVersion,
                    matchingVersions,
                    matchingLoaders
            );
        }

        if ("folia".equals(soft) && !loaders.contains("folia") && (loaders.contains("paper") || loaders.contains("spigot"))) {
            return new CompatibilityResult(
                    CompatibilityResult.Status.NEEDS_ANALYSIS,
                    "Suporta Paper/Spigot, mas não declara suporte explícito ao Folia (threads regionais)",
                    matchingVersions,
                    matchingLoaders
            );
        }

        if (loaderMatch && !versionMatch) {
            return new CompatibilityResult(
                    CompatibilityResult.Status.PARTIAL_COMPATIBILITY,
                    "Plataforma " + serverSoftware + " suportada, mas a versão exata do Minecraft (" + serverVersion + ") não foi listada",
                    matchingVersions,
                    matchingLoaders
            );
        }

        if (!loaderMatch && versionMatch) {
            return new CompatibilityResult(
                    CompatibilityResult.Status.PARTIAL_COMPATIBILITY,
                    "Versão do Minecraft suportada (" + serverVersion + "), mas loader não coincide",
                    matchingVersions,
                    matchingLoaders
            );
        }

        if (loaders.isEmpty() && gameVersions.isEmpty()) {
            return new CompatibilityResult(
                    CompatibilityResult.Status.UNCONFIRMED,
                    "Metadados de compatibilidade não fornecidos pelo autor",
                    matchingVersions,
                    matchingLoaders
            );
        }

        return new CompatibilityResult(
                CompatibilityResult.Status.INCOMPATIBLE,
                "Incompatível com o ambiente atual (" + serverSoftware + " " + serverVersion + ")",
                matchingVersions,
                matchingLoaders
        );
    }

    // A listed "1.21" covers a 1.21.4 server; prefixes only count on a '.' boundary, so 1.21.1 never matches 1.21.10.
    private static boolean isVersionFamily(String listedVersion, String serverVersion) {
        return !listedVersion.isEmpty() && serverVersion.toLowerCase().startsWith(listedVersion.toLowerCase() + ".");
    }
}
