package br.com.craftpanel.backups;

import java.time.Instant;

public record BackupMetadata(
        String id,
        String serverId,
        String filename,
        long fileSizeBytes,
        String checksum,
        String createdBy,
        String notes,
        Instant createdAt
) {}
