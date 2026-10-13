package br.com.craftpanel.config;

public record PanelConfig(
        ServerSettings server,
        SecuritySettings security,
        StorageSettings storage,
        ModrinthSettings modrinth,
        MonitoringSettings monitoring,
        LoggingSettings logging
) {
    public record ServerSettings(String host, int port) {}
    public record SecuritySettings(
            boolean remoteAccessEnabled,
            int sessionTimeoutMinutes,
            boolean requireSecureCookies,
            int maxFailedLoginAttempts,
            int lockoutDurationMinutes
    ) {}
    public record StorageSettings(
            String dataDirectory,
            String database,
            String backupDirectory,
            String serversDirectory
    ) {}
    public record ModrinthSettings(
            boolean enabled,
            String userAgent,
            String apiUrl
    ) {}
    public record MonitoringSettings(
            int refreshIntervalSeconds,
            int maxHistoryPoints
    ) {}
    public record LoggingSettings(String level) {}
}
