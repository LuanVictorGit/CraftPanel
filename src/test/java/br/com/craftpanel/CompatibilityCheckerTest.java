package br.com.craftpanel;

import br.com.craftpanel.modrinth.CompatibilityChecker;
import br.com.craftpanel.modrinth.CompatibilityResult;
import br.com.craftpanel.modrinth.ModrinthVersion;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CompatibilityCheckerTest {

    @Test
    void testPaperCompatibleConfirmed() {
        ModrinthVersion version = new ModrinthVersion(
                "v1", "p1", "Version 1.0", "1.0",
                List.of("1.20.4", "1.20.2"),
                List.of("paper", "spigot"),
                Collections.emptyList(),
                Collections.emptyList(),
                "Changelog",
                "2026-01-01"
        );

        CompatibilityResult result = CompatibilityChecker.check("paper", "1.20.4", version);
        assertEquals(CompatibilityResult.Status.COMPATIBLE_CONFIRMED, result.status());
    }

    @Test
    void testFoliaNeedsExplicitLoader() {
        ModrinthVersion version = new ModrinthVersion(
                "v1", "p1", "Version 1.0", "1.0",
                List.of("1.20.4"),
                List.of("paper"),
                Collections.emptyList(),
                Collections.emptyList(),
                "Changelog",
                "2026-01-01"
        );

        CompatibilityResult result = CompatibilityChecker.check("folia", "1.20.4", version);
        assertEquals(CompatibilityResult.Status.NEEDS_ANALYSIS, result.status());
    }

    @Test
    void testLegacyMinecraftVersionSupport() {
        ModrinthVersion version = new ModrinthVersion(
                "v1", "p1", "Version 1.8", "1.8",
                List.of("1.8.8", "1.8"),
                List.of("spigot", "bukkit"),
                Collections.emptyList(),
                Collections.emptyList(),
                "Changelog",
                "2026-01-01"
        );

        CompatibilityResult result = CompatibilityChecker.check("spigot", "1.8.8", version);
        assertEquals(CompatibilityResult.Status.COMPATIBLE_CONFIRMED, result.status());
    }

    @Test
    void testPatchVersionsOnlyMatchOnDotBoundary() {
        ModrinthVersion newer = version(List.of("1.21.10", "1.21.11"), List.of("paper"));
        assertEquals(CompatibilityResult.Status.PARTIAL_COMPATIBILITY, CompatibilityChecker.check("paper", "1.21.1", newer).status());

        ModrinthVersion older = version(List.of("1.21.1"), List.of("paper"));
        assertEquals(CompatibilityResult.Status.PARTIAL_COMPATIBILITY, CompatibilityChecker.check("paper", "1.21.10", older).status());
    }

    @Test
    void testMinorListingCoversPatchServer() {
        ModrinthVersion listing = version(List.of("1.21"), List.of("paper"));
        CompatibilityResult result = CompatibilityChecker.check("paper", "1.21.4", listing);
        assertEquals(CompatibilityResult.Status.COMPATIBLE_CONFIRMED, result.status());
        assertEquals(List.of("1.21"), result.matchingVersions());
    }

    @Test
    void testPurpurLoader() {
        CompatibilityResult purpurOnly = CompatibilityChecker.check("Purpur", "1.21.4", version(List.of("1.21.4"), List.of("purpur")));
        assertEquals(CompatibilityResult.Status.COMPATIBLE_CONFIRMED, purpurOnly.status());
        assertEquals(List.of("purpur"), purpurOnly.matchingLoaders());

        CompatibilityResult paperPlugin = CompatibilityChecker.check("Purpur", "1.21.4", version(List.of("1.21.4"), List.of("paper", "spigot")));
        assertEquals(CompatibilityResult.Status.COMPATIBLE_CONFIRMED, paperPlugin.status());
        assertEquals(List.of("paper"), paperPlugin.matchingLoaders());

        CompatibilityResult onPaper = CompatibilityChecker.check("Paper", "1.21.4", version(List.of("1.21.4"), List.of("purpur")));
        assertNotEquals(CompatibilityResult.Status.COMPATIBLE_CONFIRMED, onPaper.status());
    }

    @Test
    void testServerNamesMapToForkLoader() {
        CompatibilityResult craftBukkit = CompatibilityChecker.check("CraftBukkit", "1.21.4", version(List.of("1.21.4"), List.of("bukkit")));
        assertEquals(List.of("bukkit"), craftBukkit.matchingLoaders());

        CompatibilityResult fallbackName = CompatibilityChecker.check("Spigot / Paper", "1.21.4", version(List.of("1.21.4"), List.of("spigot")));
        assertEquals(CompatibilityResult.Status.COMPATIBLE_CONFIRMED, fallbackName.status());
    }

    private static ModrinthVersion version(List<String> gameVersions, List<String> loaders) {
        return new ModrinthVersion(
                "v1", "p1", "Version 1.0", "1.0",
                gameVersions,
                loaders,
                Collections.emptyList(),
                Collections.emptyList(),
                "Changelog",
                "2026-01-01"
        );
    }
}
