package br.com.craftpanel.files;

import br.com.craftpanel.servers.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class FileManager {
    private static final long MAX_EDITABLE_SIZE_BYTES = 5 * 1024 * 1024;

    public List<FileItem> listFiles(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path dir = PathValidator.resolveAndValidate(root, relativePath);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Directory does not exist: " + relativePath);
        }

        List<FileItem> items = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                boolean isDir = Files.isDirectory(path);
                long size = isDir ? 0 : Files.size(path);
                Instant lastMod = Files.getLastModifiedTime(path).toInstant();
                String name = path.getFileName().toString();
                String ext = isDir ? "" : getExtension(name);
                String rel = root.relativize(path).toString().replace('\\', '/');

                items.add(new FileItem(name, rel, isDir, size, lastMod, ext));
            }
        }

        items.sort((a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.name().compareToIgnoreCase(b.name());
        });

        return items;
    }

    public String readFile(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path file = PathValidator.resolveAndValidate(root, relativePath);

        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new IllegalArgumentException("File not found or is a directory: " + relativePath);
        }

        long size = Files.size(file);
        if (size > MAX_EDITABLE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum editable size of 5 MB (" + size + " bytes)");
        }

        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public void writeFile(MinecraftServer server, String relativePath, String content) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path file = PathValidator.resolveAndValidate(root, relativePath);

        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.writeString(file, content != null ? content : "", StandardCharsets.UTF_8);
    }

    public void createDirectory(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path dir = PathValidator.resolveAndValidate(root, relativePath);
        Files.createDirectories(dir);
    }

    public void createFile(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path file = PathValidator.resolveAndValidate(root, relativePath);

        if (Files.exists(file)) {
            throw new IllegalArgumentException("File already exists: " + relativePath);
        }

        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.createFile(file);
    }

    public void renameFile(MinecraftServer server, String relativePath, String newName) throws IOException {
        if (newName == null || newName.isBlank() || newName.contains("/") || newName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name: " + newName);
        }

        Path root = Paths.get(server.serverDir());
        Path source = PathValidator.resolveAndValidate(root, relativePath);

        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Source file does not exist: " + relativePath);
        }

        Path target = source.resolveSibling(newName);
        PathValidator.resolveAndValidate(root, root.relativize(target).toString());

        if (Files.exists(target)) {
            throw new IllegalArgumentException("A file or directory with that name already exists");
        }

        Files.move(source, target);
    }

    public void moveFile(MinecraftServer server, String sourceRelativePath, String targetDirRelativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path source = PathValidator.resolveAndValidate(root, sourceRelativePath);
        Path targetDir = PathValidator.resolveAndValidate(root, targetDirRelativePath);

        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Source file does not exist: " + sourceRelativePath);
        }
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("Target is not a directory: " + targetDirRelativePath);
        }

        Path target = targetDir.resolve(source.getFileName());
        PathValidator.resolveAndValidate(root, root.relativize(target).toString());

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void deleteFile(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path target = PathValidator.resolveAndValidate(root, relativePath);

        if (target.equals(root.toAbsolutePath().normalize())) {
            throw new SecurityException("Cannot delete server root directory");
        }

        if (!Files.exists(target)) {
            return;
        }

        if (Files.isDirectory(target)) {
            try (Stream<Path> stream = Files.walk(target)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } else {
            Files.delete(target);
        }
    }

    public File getFile(MinecraftServer server, String relativePath) {
        Path root = Paths.get(server.serverDir());
        Path path = PathValidator.resolveAndValidate(root, relativePath);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new IllegalArgumentException("File does not exist or is a directory: " + relativePath);
        }
        return path.toFile();
    }

    public void saveUploadedFile(MinecraftServer server, String targetDirRel, String filename, InputStream inputStream) throws IOException {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        String sanitizedFilename = Paths.get(filename).getFileName().toString();
        Path root = Paths.get(server.serverDir());
        Path targetDir = PathValidator.resolveAndValidate(root, targetDirRel);

        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path targetFile = targetDir.resolve(sanitizedFilename);
        PathValidator.resolveAndValidate(root, root.relativize(targetFile).toString());

        Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
    }
}
