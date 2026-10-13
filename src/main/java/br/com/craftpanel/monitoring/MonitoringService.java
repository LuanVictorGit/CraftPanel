package br.com.craftpanel.monitoring;

import br.com.craftpanel.processes.ProcessManager;
import br.com.craftpanel.processes.ProcessState;
import br.com.craftpanel.processes.ServerProcess;
import br.com.craftpanel.servers.MinecraftServer;
import com.sun.management.OperatingSystemMXBean;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MonitoringService {
    private final ProcessManager processManager;
    private final OperatingSystemMXBean osBean;
    private final Map<String, ServerMetrics> latestMetrics = new ConcurrentHashMap<>();

    public record SystemMetrics(
            double systemCpuPercent,
            double processCpuPercent,
            long totalPhysicalMemoryBytes,
            long freePhysicalMemoryBytes,
            long usedPhysicalMemoryBytes,
            long totalDiskBytes,
            long freeDiskBytes,
            long usedDiskBytes,
            int availableProcessors,
            String javaVersion
    ) {}

    public MonitoringService(ProcessManager processManager) {
        this.processManager = processManager;
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public ServerMetrics collectMetrics(MinecraftServer server) {
        ServerProcess proc = processManager.getProcess(server.id());
        ProcessState state = proc != null ? proc.getState() : ProcessState.STOPPED;
        long uptime = proc != null ? proc.getUptime().toSeconds() : 0;

        File serverDir = new File(server.serverDir());
        long diskTotal = serverDir.getTotalSpace();
        long diskFree = serverDir.getUsableSpace();
        long diskUsed = Math.max(0, diskTotal - diskFree);

        double cpu = 0.0;
        long memUsed = 0;
        long memMax = server.maxMemoryMb();

        MinecraftPing.PingResult ping = MinecraftPing.PingResult.offline();
        if (state == ProcessState.RUNNING || state == ProcessState.STARTING) {
            cpu = Math.max(0.0, osBean.getProcessCpuLoad() * 100.0);
            memUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
            ping = MinecraftPing.ping("127.0.0.1", server.serverPort(), 1500);
        }

        int onlinePlayers = ping.onlinePlayers();
        int maxPlayers = ping.maxPlayers();
        String motd = ping.motd();

        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object serverObj = bukkitClass.getMethod("getServer").invoke(null);
            if (serverObj != null) {
                onlinePlayers = ((java.util.Collection<?>) bukkitClass.getMethod("getOnlinePlayers").invoke(null)).size();
                maxPlayers = (int) bukkitClass.getMethod("getMaxPlayers").invoke(null);
                motd = (String) bukkitClass.getMethod("getMotd").invoke(null);
            }
        } catch (Throwable ignored) {}

        ServerMetrics metrics = new ServerMetrics(
                server.id(),
                state.name(),
                Math.round(cpu * 10.0) / 10.0,
                memUsed,
                memMax,
                Math.max(0, memMax - memUsed),
                diskUsed,
                diskTotal,
                uptime,
                onlinePlayers,
                maxPlayers,
                ping.pingMs(),
                motd
        );

        latestMetrics.put(server.id(), metrics);
        return metrics;
    }

    public SystemMetrics collectSystemMetrics() {
        double sysCpu = Math.max(0.0, osBean.getCpuLoad() * 100.0);
        double procCpu = Math.max(0.0, osBean.getProcessCpuLoad() * 100.0);
        long totalRam = osBean.getTotalMemorySize();
        long freeRam = osBean.getFreeMemorySize();
        long usedRam = Math.max(0, totalRam - freeRam);

        File root = new File(".");
        long totalDisk = root.getTotalSpace();
        long freeDisk = root.getUsableSpace();
        long usedDisk = Math.max(0, totalDisk - freeDisk);

        return new SystemMetrics(
                Math.round(sysCpu * 10.0) / 10.0,
                Math.round(procCpu * 10.0) / 10.0,
                totalRam,
                freeRam,
                usedRam,
                totalDisk,
                freeDisk,
                usedDisk,
                osBean.getAvailableProcessors(),
                System.getProperty("java.version")
        );
    }

    public ServerMetrics getLatest(String serverId) {
        return latestMetrics.get(serverId);
    }
}
