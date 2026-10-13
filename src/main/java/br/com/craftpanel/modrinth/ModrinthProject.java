package br.com.craftpanel.modrinth;

import java.util.List;

public record ModrinthProject(
        String id,
        String slug,
        String title,
        String description,
        List<String> categories,
        String clientSide,
        String serverSide,
        String iconUrl,
        int downloads,
        int follows,
        String dateUpdated,
        String author
) {}
