package br.com.craftpanel.monitoring;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Arrays;

public class TickMonitor implements Runnable {
    private static final int HISTORY_SIZE = 100;
    private static TickMonitor instance;

    private final long[] tickDurationsNanos = new long[HISTORY_SIZE];
    private int tickIndex = 0;
    private long lastTickTime = System.nanoTime();
    private int taskId = -1;
    private Object foliaTask = null;

    public static synchronized TickMonitor getInstance() {
        if (instance == null) {
            instance = new TickMonitor();
        }
        return instance;
    }

    public synchronized void start(Plugin plugin) {
        if (taskId != -1 || foliaTask != null) {
            return;
        }

        lastTickTime = System.nanoTime();
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Method getGlobal = bukkitClass.getMethod("getGlobalRegionScheduler");
            Object scheduler = getGlobal.invoke(null);
            Method runAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            this.foliaTask = runAtFixedRate.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) o -> tick(), 1L, 1L);
        } catch (Throwable t) {
            try {
                this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 1L, 1L);
            } catch (Throwable ignored) {}
        }
    }

    public synchronized void stop() {
        if (taskId != -1) {
            try {
                Bukkit.getScheduler().cancelTask(taskId);
            } catch (Throwable ignored) {}
            taskId = -1;
        }
        if (foliaTask != null) {
            try {
                foliaTask.getClass().getMethod("cancel").invoke(foliaTask);
            } catch (Throwable ignored) {}
            foliaTask = null;
        }
    }

    @Override
    public void run() {
        tick();
    }

    private void tick() {
        long now = System.nanoTime();
        long duration = now - lastTickTime;
        lastTickTime = now;

        synchronized (tickDurationsNanos) {
            tickDurationsNanos[tickIndex] = duration;
            tickIndex = (tickIndex + 1) % HISTORY_SIZE;
        }
    }

    public record MsptStats(
            double currentMs,
            double medianMs,
            double p95Ms,
            double maxMs,
            double minMs
    ) {}

    public MsptStats getStats() {
        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            try {
                Method getAvg = bukkitClass.getMethod("getAverageTickTime");
                double avg = (double) getAvg.invoke(null);
                if (avg > 0) {
                    return new MsptStats(avg, avg, avg, avg, avg);
                }
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}

        long[] copy;
        synchronized (tickDurationsNanos) {
            copy = Arrays.copyOf(tickDurationsNanos, HISTORY_SIZE);
        }

        int validCount = 0;
        for (long d : copy) {
            if (d > 0) validCount++;
        }

        if (validCount == 0) {
            return new MsptStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        double[] msArray = new double[validCount];
        int idx = 0;
        for (long d : copy) {
            if (d > 0) {
                msArray[idx++] = Math.min(d / 1_000_000.0, 1000.0);
            }
        }

        Arrays.sort(msArray);

        double min = msArray[0];
        double max = msArray[msArray.length - 1];
        double median = msArray[msArray.length / 2];
        int p95Idx = Math.min((int) Math.ceil(msArray.length * 0.95) - 1, msArray.length - 1);
        double p95 = msArray[Math.max(0, p95Idx)];

        int prevIdx = (tickIndex - 1 + HISTORY_SIZE) % HISTORY_SIZE;
        double current = Math.min(copy[prevIdx] / 1_000_000.0, 1000.0);

        return new MsptStats(
                Math.round(current * 10.0) / 10.0,
                Math.round(median * 10.0) / 10.0,
                Math.round(p95 * 10.0) / 10.0,
                Math.round(max * 10.0) / 10.0,
                Math.round(min * 10.0) / 10.0
        );
    }
}
