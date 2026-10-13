package br.com.craftpanel.modrinth;

import java.util.List;

public record CompatibilityResult(
        Status status,
        String message,
        List<String> matchingVersions,
        List<String> matchingLoaders
) {
    public enum Status {
        COMPATIBLE_CONFIRMED,
        PARTIAL_COMPATIBILITY,
        UNCONFIRMED,
        INCOMPATIBLE,
        NEEDS_ANALYSIS
    }
}
