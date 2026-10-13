package br.com.craftpanel.tasks;

import java.time.Instant;

public record Task(
        String id,
        String type,
        String targetId,
        State state,
        int progress,
        String message,
        String error,
        Instant createdAt,
        Instant completedAt
) {
    public enum State {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
