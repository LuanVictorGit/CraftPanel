package br.com.craftpanel.monitoring;

public record ServerMetrics(
        String serverId,
        String status,
        double cpuPercent,
        long memoryUsedMb,
        long memoryMaxMb,
        long memoryFreeMb,
        long diskUsedBytes,
        long diskTotalBytes,
        long uptimeSeconds,
        int playerCount,
        int maxPlayers,
        long pingMs,
        String motd
) {}
