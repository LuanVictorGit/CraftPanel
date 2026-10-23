package br.com.craftpanel.files;

import br.com.craftpanel.config.PanelConfig;
import br.com.craftpanel.servers.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final String INTERNAL_DATA_DENIED = "CraftPanel's internal data cannot be accessed from the file manager";

    public record TextContent(String content, String charset) {}

    private final List<Path> internalPaths = new ArrayList<>();

    public FileManager(PanelConfig config) {
        PanelConfig.StorageSettings storage = config.storage();
        addInternalPath(storage.dataDirectory());
        addInternalPath(storage.backupDirectory());
        String database = storage.database();
        if (database != null && !database.isBlank()) {
            for (String suffix : List.of("", "-wal", "-shm", "-journal")) {
                addInternalPath(database + suffix);
            }
        }
    }

    private void addInternalPath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            internalPaths.add(Paths.get(path).toAbsolutePath().normalize());
        } catch (InvalidPathException ignored) {}
    }

    public List<FileItem> listFiles(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path dir = PathValidator.resolveAndValidate(root, relativePath);
        requireNotInternal(root, dir);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Directory does not exist: " + relativePath);
        }

        List<Path> hidden = internalRealPaths(root);
        Path realDir = PathValidator.realPath(dir);
        List<FileItem> items = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (isInside(realDir.resolve(name), hidden)) {
                    continue;
                }
                boolean isDir = Files.isDirectory(path);
                long size = isDir ? 0 : Files.size(path);
                Instant lastMod = Files.getLastModifiedTime(path).toInstant();
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
        return readTextFile(server, relativePath).content();
    }

    public TextContent readTextFile(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path file = PathValidator.resolveAndValidate(root, relativePath);
        requireNotInternal(root, file);

        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new IllegalArgumentException("File not found or is a directory: " + relativePath);
        }

        long size = Files.size(file);
        if (size > MAX_EDITABLE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum editable size of 5 MB (" + size + " bytes)");
        }

        TextContent text = decodeText(Files.readAllBytes(file));
        if (text == null) {
            throw new IllegalArgumentException("File is not UTF-8 or Windows-1252 text: " + relativePath);
        }
        return text;
    }

    public void writeFile(MinecraftServer server, String relativePath, String content) throws IOException {
        writeFile(server, relativePath, content, null);
    }

    /**
     * Saves text in {@code charsetName} ("UTF-8" or "windows-1252"). Without one, a file that currently decodes only
     * as Windows-1252 is written back in Windows-1252; anything that charset cannot represent is saved as UTF-8.
     */
    public void writeFile(MinecraftServer server, String relativePath, String content, String charsetName) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path file = PathValidator.resolveAndValidate(root, relativePath);
        requireNotInternal(root, file);

        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        String text = content != null ? content : "";
        Charset charset = charsetName != null && !charsetName.isBlank() ? parseCharset(charsetName) : currentCharset(file);
        if (!charset.newEncoder().canEncode(text)) {
            charset = StandardCharsets.UTF_8;
        }
        Files.writeString(file, text, charset);
    }

    public void createDirectory(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path dir = PathValidator.resolveAndValidate(root, relativePath);
        requireNotInternal(root, dir);
        Files.createDirectories(dir);
    }

    public void createFile(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path file = PathValidator.resolveAndValidate(root, relativePath);
        requireNotInternal(root, file);

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
        requireNoInternalData(root, source);

        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Source file does not exist: " + relativePath);
        }

        Path target = source.resolveSibling(newName);
        PathValidator.resolveAndValidate(root, root.relativize(target).toString());
        requireNotInternal(root, target);

        if (Files.exists(target)) {
            throw new IllegalArgumentException("A file or directory with that name already exists");
        }

        Files.move(source, target);
    }

    public void moveFile(MinecraftServer server, String sourceRelativePath, String targetDirRelativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path source = PathValidator.resolveAndValidate(root, sourceRelativePath);
        Path targetDir = PathValidator.resolveAndValidate(root, targetDirRelativePath);
        requireNoInternalData(root, source);
        requireNotInternal(root, targetDir);

        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Source file does not exist: " + sourceRelativePath);
        }
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("Target is not a directory: " + targetDirRelativePath);
        }

        Path target = targetDir.resolve(source.getFileName());
        PathValidator.resolveAndValidate(root, root.relativize(target).toString());
        requireNotInternal(root, target);

        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void deleteFile(MinecraftServer server, String relativePath) throws IOException {
        Path root = Paths.get(server.serverDir());
        Path target = PathValidator.resolveAndValidate(root, relativePath);

        if (target.toAbsolutePath().normalize().equals(root.toAbsolutePath().normalize())) {
            throw new SecurityException("Cannot delete server root directory");
        }
        requireNoInternalData(root, target);

        if (!Files.exists(target)) {
            return;
        }

        if (Files.isDirectory(target)) {
            List<Path> paths;
            try (Stream<Path> stream = Files.walk(target)) {
                paths = stream.sorted(Comparator.reverseOrder()).toList();
            }
            List<String> failures = new ArrayList<>();
            for (Path p : paths) {
                try {
                    deleteForcibly(p);
                } catch (DirectoryNotEmptyException e) {
                    // Expected when something inside it could not be deleted; that item is already reported.
                    if (failures.isEmpty()) {
                        failures.add(displayPath(root, p) + " (not empty)");
                    }
                } catch (IOException e) {
                    failures.add(displayPath(root, p));
                }
            }
            if (!failures.isEmpty()) {
                int shown = Math.min(failures.size(), 10);
                throw new IOException("Could not delete " + failures.size() + " item(s), probably in use by the server or read-only: "
                        + String.join(", ", failures.subList(0, shown))
                        + (failures.size() > shown ? ", ..." : ""));
            }
        } else {
            deleteForcibly(target);
        }
    }

    private static void deleteForcibly(Path path) throws IOException {
        try {
            Files.deleteIfExists(path);
        } catch (DirectoryNotEmptyException e) {
            throw e;
        } catch (IOException e) {
            File f = path.toFile();
            f.setWritable(true);
            if (!f.delete() && f.exists()) {
                throw e;
            }
        }
    }

    private static String displayPath(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path).toString().replace('\\', '/');
    }

    public File getFile(MinecraftServer server, String relativePath) {
        Path root = Paths.get(server.serverDir());
        Path path = PathValidator.resolveAndValidate(root, relativePath);
        requireNotInternal(root, path);
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
        requireNotInternal(root, targetDir);

        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        Path targetFile = targetDir.resolve(sanitizedFilename);
        PathValidator.resolveAndValidate(root, root.relativize(targetFile).toString());
        requireNotInternal(root, targetFile);

        try (inputStream) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // CraftPanel's database, backups and data folder, by real path. A configured folder that contains the server
    // root itself is skipped, otherwise the whole file manager would be blocked.
    private List<Path> internalRealPaths(Path root) {
        Path realRoot = PathValidator.realPath(root);
        List<Path> real = new ArrayList<>(internalPaths.size());
        for (Path internal : internalPaths) {
            Path realInternal = PathValidator.realPath(internal);
            if (!realRoot.startsWith(realInternal)) {
                real.add(realInternal);
            }
        }
        return real;
    }

    private void requireNotInternal(Path root, Path path) {
        if (isInside(PathValidator.realPath(path), internalRealPaths(root))) {
            throw new SecurityException(INTERNAL_DATA_DENIED);
        }
    }

    // Deleting, moving or renaming a folder that holds CraftPanel's data would take that data along.
    private void requireNoInternalData(Path root, Path path) {
        Path real = PathValidator.realPath(path);
        for (Path internal : internalRealPaths(root)) {
            if (real.startsWith(internal) || internal.startsWith(real)) {
                throw new SecurityException(INTERNAL_DATA_DENIED);
            }
        }
    }

    private static boolean isInside(Path path, List<Path> parents) {
        for (Path parent : parents) {
            if (path.startsWith(parent)) {
                return true;
            }
        }
        return false;
    }

    private static TextContent decodeText(byte[] bytes) {
        try {
            return new TextContent(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(), StandardCharsets.UTF_8.name());
        } catch (CharacterCodingException notUtf8) {
            try {
                return new TextContent(WINDOWS_1252.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(), WINDOWS_1252.name());
            } catch (CharacterCodingException notWindows1252) {
                return null;
            }
        }
    }

    private static Charset parseCharset(String name) {
        String trimmed = name.trim();
        return trimmed.equalsIgnoreCase(WINDOWS_1252.name()) || trimmed.equalsIgnoreCase("cp1252")
                ? WINDOWS_1252
                : StandardCharsets.UTF_8;
    }

    private static Charset currentCharset(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_EDITABLE_SIZE_BYTES) {
            return StandardCharsets.UTF_8;
        }
        TextContent existing = decodeText(Files.readAllBytes(file));
        return existing != null && WINDOWS_1252.name().equals(existing.charset()) ? WINDOWS_1252 : StandardCharsets.UTF_8;
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
    }
}
