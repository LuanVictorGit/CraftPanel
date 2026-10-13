package br.com.craftpanel.users;

import java.util.List;

public record Role(
        String id,
        String name,
        String description,
        boolean isSystem,
        List<String> permissions
) {}
