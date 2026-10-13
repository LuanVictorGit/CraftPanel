package br.com.craftpanel.modrinth;

import java.util.List;
import java.util.Map;

public record ModrinthVersion(
        String id,
        String projectId,
        String name,
        String versionNumber,
        List<String> gameVersions,
        List<String> loaders,
        List<VersionFile> files,
        List<VersionDependency> dependencies,
        String changelog,
        String datePublished
) {
    public record VersionFile(
            String url,
            String filename,
            boolean primary,
            long sizeBytes,
            Map<String, String> hashes
    ) {}

    public record VersionDependency(
            String versionId,
            String projectId,
            String fileName,
            String dependencyType
    ) {}
}
