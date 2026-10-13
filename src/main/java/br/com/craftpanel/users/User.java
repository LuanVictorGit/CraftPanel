package br.com.craftpanel.users;

import java.time.Instant;

public record User(
        String id,
        String username,
        String displayName,
        String roleId,
        Instant createdAt,
        Instant updatedAt
) {}
