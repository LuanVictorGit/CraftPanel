package br.com.craftpanel.audit;

import java.time.Instant;

public record AuditLog(
        String id,
        String userId,
        String username,
        String action,
        String targetType,
        String targetId,
        String details,
        String ipAddress,
        String status,
        Instant createdAt
) {}
