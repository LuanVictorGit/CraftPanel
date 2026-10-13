package br.com.craftpanel.api;

import br.com.craftpanel.monitoring.MonitoringService;
import br.com.craftpanel.monitoring.ServerMetrics;
import br.com.craftpanel.monitoring.SparkTelemetry;
import br.com.craftpanel.servers.MinecraftServer;
import br.com.craftpanel.servers.ServerService;
import br.com.craftpanel.users.Permission;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

public class MonitoringController {
    private final MonitoringService monitoringService;
    private final ServerService serverService;
    private final AuthMiddleware authMiddleware;

    public MonitoringController(MonitoringService monitoringService, ServerService serverService, AuthMiddleware authMiddleware) {
        this.monitoringService = monitoringService;
        this.serverService = serverService;
        this.authMiddleware = authMiddleware;
    }

    public void getSystemMetrics(Context ctx) {
        authMiddleware.requirePermission(ctx, Permission.MONITORING_VIEW);
        MonitoringService.SystemMetrics metrics = monitoringService.collectSystemMetrics();
        ctx.json(metrics);
    }

    public void getServerMetrics(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.SERVER_VIEW);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        ServerMetrics metrics = monitoringService.collectMetrics(server);
        ctx.json(metrics);
    }

    public void getSparkTelemetry(Context ctx) {
        String serverId = ctx.pathParam("id");
        authMiddleware.requireServerPermission(ctx, serverId, Permission.MONITORING_VIEW);
        MinecraftServer server = serverService.getServer(serverId).orElseThrow(() -> new NotFoundResponse("Server not found"));

        SparkTelemetry telemetry = monitoringService.collectSparkTelemetry(server);
        ctx.json(telemetry);
    }
}
