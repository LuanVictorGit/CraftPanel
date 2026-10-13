package br.com.craftpanel.monitoring;

import java.util.List;

public record SparkTelemetry(
        long timestamp,
        ServerInfo server,
        TpsInfo tps,
        CpuInfo cpu,
        MemoryInfo memory,
        WorldSummary worldsSummary,
        PluginSummary pluginsSummary
) {
    public record ServerInfo(
            String name,
            String status,
            String software,
            String minecraftVersion,
            String bukkitVersion,
            String javaVersion,
            String javaVendor,
            String osName,
            long uptimeSeconds
    ) {}

    public record TpsInfo(
            double tps1m,
            double tps5m,
            double tps15m,
            double msptCurrent,
            double msptMedian,
            double mspt95th,
            double msptMax
    ) {}

    public record CpuInfo(
            double systemCpuPercent,
            double processCpuPercent,
            int cores,
            int threadCount
    ) {}

    public record MemoryInfo(
            long heapUsedMb,
            long heapMaxMb,
            long heapCommittedMb,
            double heapPercent,
            long nonHeapUsedMb,
            long nonHeapCommittedMb,
            double physicalUsedGb,
            double physicalTotalGb,
            double physicalFreeGb,
            double physicalPercent,
            List<GcInfo> gc
    ) {}

    public record GcInfo(
            String name,
            long count,
            long timeMs
    ) {}

    public record WorldSummary(
            int totalChunks,
            int totalEntities,
            int totalPlayers,
            List<WorldTelemetry> worlds
    ) {}

    public record WorldTelemetry(
            String name,
            String environment,
            int loadedChunks,
            int totalEntities,
            int players,
            int livingEntities,
            int items,
            int otherEntities
    ) {}

    public record PluginSummary(
            int total,
            int enabled,
            int disabled,
            List<PluginTelemetry> list
    ) {}

    public record PluginTelemetry(
            String name,
            String version,
            boolean enabled,
            String author,
            String description
    ) {}
}
