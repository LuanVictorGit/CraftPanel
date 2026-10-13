package br.com.craftpanel.monitoring;

import br.com.craftpanel.processes.ProcessManager;
import br.com.craftpanel.processes.ProcessState;
import br.com.craftpanel.processes.ServerProcess;
import br.com.craftpanel.servers.MinecraftServer;
import com.sun.management.OperatingSystemMXBean;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MonitoringService {
    private static final int MAX_HISTORY_POINTS = 720;
    private static final long HISTORY_SAMPLE_INTERVAL_MS = 5000L;

    private final ProcessManager processManager;
    private final OperatingSystemMXBean osBean;
    private final Map<String, ServerMetrics> latestMetrics = new ConcurrentHashMap<>();
    private final Map<String, SparkTelemetry> latestSpark = new ConcurrentHashMap<>();
    private final Map<String, Deque<SparkTelemetry.MetricHistoryPoint>> historyMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastHistoryTime = new ConcurrentHashMap<>();

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

    public SparkTelemetry collectSparkTelemetry(MinecraftServer server) {
        long timestamp = System.currentTimeMillis();
        ServerProcess proc = processManager.getProcess(server.id());
        ProcessState state = proc != null ? proc.getState() : ProcessState.STOPPED;
        long uptime = proc != null ? proc.getUptime().toSeconds() : 0;

        double[] tps = getTps();
        TickMonitor.MsptStats mspt = TickMonitor.getInstance().getStats();
        SparkTelemetry.TpsInfo tpsInfo = new SparkTelemetry.TpsInfo(
                Math.round(tps[0] * 100.0) / 100.0,
                Math.round(tps[1] * 100.0) / 100.0,
                Math.round(tps[2] * 100.0) / 100.0,
                mspt.currentMs(),
                mspt.medianMs(),
                mspt.p95Ms(),
                mspt.maxMs()
        );

        double sysCpu = Math.max(0.0, osBean.getCpuLoad() * 100.0);
        double procCpu = Math.max(0.0, osBean.getProcessCpuLoad() * 100.0);
        int cores = osBean.getAvailableProcessors();
        int threads = Thread.activeCount();
        SparkTelemetry.CpuInfo cpuInfo = new SparkTelemetry.CpuInfo(
                Math.round(sysCpu * 10.0) / 10.0,
                Math.round(procCpu * 10.0) / 10.0,
                cores,
                threads
        );

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

        long heapUsedMb = heap.getUsed() / (1024 * 1024);
        long heapMaxMb = Math.max(heap.getMax(), heap.getCommitted()) / (1024 * 1024);
        long heapCommMb = heap.getCommitted() / (1024 * 1024);
        double heapPct = heapMaxMb > 0 ? (heapUsedMb * 100.0 / heapMaxMb) : 0.0;

        long nonHeapUsedMb = nonHeap.getUsed() / (1024 * 1024);
        long nonHeapCommMb = nonHeap.getCommitted() / (1024 * 1024);

        long totalRam = osBean.getTotalMemorySize();
        long freeRam = osBean.getFreeMemorySize();
        long usedRam = Math.max(0, totalRam - freeRam);
        double physTotalGb = Math.round((totalRam / (1024.0 * 1024.0 * 1024.0)) * 10.0) / 10.0;
        double physUsedGb = Math.round((usedRam / (1024.0 * 1024.0 * 1024.0)) * 10.0) / 10.0;
        double physFreeGb = Math.round((freeRam / (1024.0 * 1024.0 * 1024.0)) * 10.0) / 10.0;
        double physPct = totalRam > 0 ? (usedRam * 100.0 / totalRam) : 0.0;

        List<SparkTelemetry.GcInfo> gcList = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcList.add(new SparkTelemetry.GcInfo(gc.getName(), gc.getCollectionCount(), gc.getCollectionTime()));
        }

        SparkTelemetry.MemoryInfo memInfo = new SparkTelemetry.MemoryInfo(
                heapUsedMb,
                heapMaxMb,
                heapCommMb,
                Math.round(heapPct * 10.0) / 10.0,
                nonHeapUsedMb,
                nonHeapCommMb,
                physUsedGb,
                physTotalGb,
                physFreeGb,
                Math.round(physPct * 10.0) / 10.0,
                gcList
        );

        SparkTelemetry.WorldSummary worldsSummary = TickMonitor.getInstance().getCachedWorldSummary();
        if (worldsSummary == null || (worldsSummary.worlds() != null && worldsSummary.worlds().isEmpty())) {
            try {
                Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
                List<?> worlds = (List<?>) bukkitClass.getMethod("getWorlds").invoke(null);
                if (worlds != null && !worlds.isEmpty()) {
                    List<SparkTelemetry.WorldTelemetry> fallbackList = new ArrayList<>();
                    int totalChunks = 0;
                    int totalEntities = 0;
                    int totalPlayers = 0;
                    for (Object w : worlds) {
                        String wName = (String) w.getClass().getMethod("getName").invoke(w);
                        Object env = w.getClass().getMethod("getEnvironment").invoke(w);
                        String envName = env != null ? env.toString() : "NORMAL";
                        int playerCount = ((List<?>) w.getClass().getMethod("getPlayers").invoke(w)).size();
                        totalPlayers += playerCount;
                        fallbackList.add(new SparkTelemetry.WorldTelemetry(wName, envName, 0, 0, playerCount, 0, 0, 0));
                    }
                    worldsSummary = new SparkTelemetry.WorldSummary(totalChunks, totalEntities, totalPlayers, fallbackList);
                }
            } catch (Throwable ignored) {}
        }
        if (worldsSummary == null) {
            worldsSummary = new SparkTelemetry.WorldSummary(0, 0, 0, List.of());
        }

        int plugTotal = 0;
        int plugEnabled = 0;
        int plugDisabled = 0;
        List<SparkTelemetry.PluginTelemetry> plugList = new ArrayList<>();

        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object pm = bukkitClass.getMethod("getPluginManager").invoke(null);
            Method getPlugins = pm.getClass().getMethod("getPlugins");
            Object[] plugins = (Object[]) getPlugins.invoke(pm);
            if (plugins != null) {
                plugTotal = plugins.length;
                PluginCpuProfiler profiler = PluginCpuProfiler.getInstance();

                for (Object p : plugins) {
                    Method getName = p.getClass().getMethod("getName");
                    String pName = (String) getName.invoke(p);

                    Method isEnabled = p.getClass().getMethod("isEnabled");
                    boolean pEnabled = (boolean) isEnabled.invoke(p);
                    if (pEnabled) plugEnabled++; else plugDisabled++;

                    Method getDesc = p.getClass().getMethod("getDescription");
                    Object desc = getDesc.invoke(p);
                    String version = "Unknown";
                    String author = "Unknown";
                    String description = "";
                    if (desc != null) {
                        Method getVer = desc.getClass().getMethod("getVersion");
                        version = (String) getVer.invoke(desc);

                        Method getAuthors = desc.getClass().getMethod("getAuthors");
                        List<?> authors = (List<?>) getAuthors.invoke(desc);
                        if (authors != null && !authors.isEmpty()) {
                            author = String.join(", ", authors.stream().map(String::valueOf).toList());
                        }

                        Method getPluginDesc = desc.getClass().getMethod("getDescription");
                        description = (String) getPluginDesc.invoke(desc);
                    }

                    PluginCpuProfiler.PluginStats stats = profiler.getStats(pName, procCpu);

                    plugList.add(new SparkTelemetry.PluginTelemetry(
                            pName,
                            version != null ? version : "",
                            pEnabled,
                            author,
                            description != null ? description : "",
                            stats.cpuPercent(),
                            stats.topClass(),
                            stats.topClassPercent()
                    ));
                }
            }
        } catch (Throwable ignored) {}

        SparkTelemetry.PluginSummary pluginsSummary = new SparkTelemetry.PluginSummary(
                plugTotal,
                plugEnabled,
                plugDisabled,
                plugList
        );

        String serverSoftware = server.software();
        String mcVer = server.minecraftVersion();
        String bukkitVer = "";
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            bukkitVer = (String) bukkitClass.getMethod("getBukkitVersion").invoke(null);
            serverSoftware = (String) bukkitClass.getMethod("getName").invoke(null);
        } catch (Throwable ignored) {}

        SparkTelemetry.ServerInfo serverInfo = new SparkTelemetry.ServerInfo(
                server.name(),
                state.name(),
                serverSoftware,
                mcVer,
                bukkitVer,
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("os.name"),
                uptime
        );

        Deque<SparkTelemetry.MetricHistoryPoint> historyDeque = historyMap.computeIfAbsent(server.id(), k -> new ArrayDeque<>());
        long lastSample = lastHistoryTime.getOrDefault(server.id(), 0L);
        if (timestamp - lastSample >= HISTORY_SAMPLE_INTERVAL_MS || historyDeque.isEmpty()) {
            SparkTelemetry.MetricHistoryPoint pt = new SparkTelemetry.MetricHistoryPoint(
                    timestamp,
                    Math.round(procCpu * 10.0) / 10.0,
                    Math.round(sysCpu * 10.0) / 10.0,
                    Math.round(heapPct * 10.0) / 10.0,
                    Math.round(physPct * 10.0) / 10.0,
                    Math.round(tps[0] * 100.0) / 100.0,
                    mspt.currentMs()
            );
            synchronized (historyDeque) {
                if (historyDeque.size() >= MAX_HISTORY_POINTS) {
                    historyDeque.removeFirst();
                }
                historyDeque.addLast(pt);
            }
            lastHistoryTime.put(server.id(), timestamp);
        }

        List<SparkTelemetry.MetricHistoryPoint> historySnapshot;
        synchronized (historyDeque) {
            historySnapshot = new ArrayList<>(historyDeque);
        }

        SparkTelemetry telemetry = new SparkTelemetry(
                timestamp,
                serverInfo,
                tpsInfo,
                cpuInfo,
                memInfo,
                worldsSummary,
                pluginsSummary,
                historySnapshot
        );

        latestSpark.put(server.id(), telemetry);
        return telemetry;
    }

    public List<SparkTelemetry.MetricHistoryPoint> getHistory(String serverId) {
        Deque<SparkTelemetry.MetricHistoryPoint> deque = historyMap.get(serverId);
        if (deque == null) return List.of();
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    private double[] getTps() {
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            try {
                Method getTps = bukkitClass.getMethod("getTPS");
                return (double[]) getTps.invoke(null);
            } catch (NoSuchMethodException e) {
                Object spigot = bukkitClass.getMethod("spigot").invoke(null);
                Method getTps = spigot.getClass().getMethod("getTPS");
                return (double[]) getTps.invoke(spigot);
            }
        } catch (Throwable ignored) {}
        return new double[]{20.0, 20.0, 20.0};
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

    public SparkTelemetry getLatestSpark(String serverId) {
        return latestSpark.get(serverId);
    }
}
