package br.com.craftpanel.monitoring;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PluginCpuProfiler implements Runnable {
    private static final int BUCKET_COUNT = 15;
    private static final long SAMPLE_INTERVAL_MS = 10L;
    private static final PluginCpuProfiler INSTANCE = new PluginCpuProfiler();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread profilerThread;
    private volatile Thread serverThread;

    private final Map<String, String> packageToPlugin = new ConcurrentHashMap<>();
    private final Map<String, String> classToPlugin = new ConcurrentHashMap<>();

    private static class SampleBucket {
        long totalSamples = 0;
        final Map<String, Long> pluginHits = new ConcurrentHashMap<>();
        final Map<String, Map<String, Long>> pluginClassHits = new ConcurrentHashMap<>();

        void clear() {
            totalSamples = 0;
            pluginHits.clear();
            pluginClassHits.clear();
        }
    }

    private final SampleBucket[] buckets = new SampleBucket[BUCKET_COUNT];
    private int currentBucketIndex = 0;
    private long lastBucketRotateTime = System.currentTimeMillis();

    public record PluginStats(
            double cpuPercent,
            String topClass,
            double topClassPercent
    ) {}

    private PluginCpuProfiler() {
        for (int i = 0; i < BUCKET_COUNT; i++) {
            buckets[i] = new SampleBucket();
        }
    }

    public static PluginCpuProfiler getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        refreshPluginPackages();

        profilerThread = new Thread(this, "craftpanel-cpu-sampler");
        profilerThread.setDaemon(true);
        profilerThread.setPriority(Thread.NORM_PRIORITY - 1);
        profilerThread.start();
    }

    public synchronized void stop() {
        running.set(false);
        if (profilerThread != null) {
            profilerThread.interrupt();
            profilerThread = null;
        }
    }

    public void setServerThread(Thread thread) {
        if (thread != null) {
            this.serverThread = thread;
        }
    }

    public void refreshPluginPackages() {
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object pm = bukkitClass.getMethod("getPluginManager").invoke(null);
            if (pm != null) {
                Plugin[] plugins = (Plugin[]) pm.getClass().getMethod("getPlugins").invoke(pm);
                if (plugins != null) {
                    for (Plugin p : plugins) {
                        registerPlugin(p);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void registerPlugin(Plugin p) {
        if (p == null) return;
        String name = p.getName();
        String main = p.getDescription().getMain();
        if (main != null && main.contains(".")) {
            String pkg = main.substring(0, main.lastIndexOf('.'));
            packageToPlugin.put(pkg, name);
            if (pkg.contains(".")) {
                String parentPkg = pkg.substring(0, pkg.lastIndexOf('.'));
                if (!parentPkg.equals("org") && !parentPkg.equals("com") && !parentPkg.equals("net") && !parentPkg.equals("io") && !parentPkg.equals("br")) {
                    packageToPlugin.put(parentPkg, name);
                }
            }
        }
        classToPlugin.put(main, name);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                sample();
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable ignored) {}
        }
    }

    private void sample() {
        long now = System.currentTimeMillis();
        if (now - lastBucketRotateTime >= 1000L) {
            synchronized (buckets) {
                currentBucketIndex = (currentBucketIndex + 1) % BUCKET_COUNT;
                buckets[currentBucketIndex].clear();
                lastBucketRotateTime = now;
            }
        }

        Thread target = serverThread;
        if (target == null || !target.isAlive()) {
            target = findServerThread();
            if (target != null) {
                this.serverThread = target;
            }
        }

        if (target == null) {
            return;
        }

        Thread.State state = target.getState();
        if (state != Thread.State.RUNNABLE) {
            return;
        }

        StackTraceElement[] trace;
        try {
            trace = target.getStackTrace();
        } catch (Throwable t) {
            return;
        }

        if (trace == null || trace.length == 0) {
            return;
        }

        SampleBucket bucket = buckets[currentBucketIndex];
        synchronized (bucket) {
            bucket.totalSamples++;
        }

        String matchedPlugin = null;
        String matchedClass = null;

        for (StackTraceElement elem : trace) {
            String className = elem.getClassName();
            String pName = resolvePlugin(className);
            if (pName != null) {
                matchedPlugin = pName;
                matchedClass = className;
                break;
            }
        }

        if (matchedPlugin != null && matchedClass != null) {
            synchronized (bucket) {
                bucket.pluginHits.merge(matchedPlugin, 1L, Long::sum);
                Map<String, Long> classMap = bucket.pluginClassHits.computeIfAbsent(matchedPlugin, k -> new ConcurrentHashMap<>());
                classMap.merge(matchedClass, 1L, Long::sum);
            }
        }
    }

    private String resolvePlugin(String className) {
        if (className == null || className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jdk.") || className.startsWith("sun.")) {
            return null;
        }
        if (className.startsWith("net.minecraft.") || className.startsWith("org.bukkit.craftbukkit.") || className.startsWith("com.mojang.")) {
            return null;
        }

        String direct = classToPlugin.get(className);
        if (direct != null) {
            return direct;
        }

        for (Map.Entry<String, String> entry : packageToPlugin.entrySet()) {
            if (className.startsWith(entry.getKey())) {
                classToPlugin.put(className, entry.getValue());
                return entry.getValue();
            }
        }

        return null;
    }

    private Thread findServerThread() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String name = t.getName();
            if (name != null && (name.contains("Server thread") || name.startsWith("folia-region-"))) {
                return t;
            }
        }
        return null;
    }

    public PluginStats getStats(String pluginName, double processCpuPercent) {
        if (pluginName == null) {
            return new PluginStats(0.0, "-", 0.0);
        }

        long totalActive = 0;
        long pluginTotal = 0;
        Map<String, Long> aggregatedClasses = new ConcurrentHashMap<>();

        synchronized (buckets) {
            for (SampleBucket b : buckets) {
                totalActive += b.totalSamples;
                Long hits = b.pluginHits.get(pluginName);
                if (hits != null) {
                    pluginTotal += hits;
                }
                Map<String, Long> classMap = b.pluginClassHits.get(pluginName);
                if (classMap != null) {
                    for (Map.Entry<String, Long> e : classMap.entrySet()) {
                        aggregatedClasses.merge(e.getKey(), e.getValue(), Long::sum);
                    }
                }
            }
        }

        if (totalActive == 0 || pluginTotal == 0) {
            return new PluginStats(0.0, "-", 0.0);
        }

        double cpuPercent = (pluginTotal / (double) totalActive) * Math.max(processCpuPercent, 0.1);
        cpuPercent = Math.min(Math.round(cpuPercent * 10.0) / 10.0, 100.0);

        String topClass = "-";
        long maxClassHits = 0;
        for (Map.Entry<String, Long> e : aggregatedClasses.entrySet()) {
            if (e.getValue() > maxClassHits) {
                maxClassHits = e.getValue();
                topClass = e.getKey();
            }
        }

        double topClassPercent = (maxClassHits / (double) pluginTotal) * 100.0;
        topClassPercent = Math.min(Math.round(topClassPercent * 10.0) / 10.0, 100.0);

        return new PluginStats(cpuPercent, simplifyClassName(topClass), topClassPercent);
    }

    private String simplifyClassName(String full) {
        if (full == null || full.equals("-")) return "-";
        int lastDot = full.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < full.length() - 1) {
            return full.substring(lastDot + 1);
        }
        return full;
    }
}
