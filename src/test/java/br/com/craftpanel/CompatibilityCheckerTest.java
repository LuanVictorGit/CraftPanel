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
}
