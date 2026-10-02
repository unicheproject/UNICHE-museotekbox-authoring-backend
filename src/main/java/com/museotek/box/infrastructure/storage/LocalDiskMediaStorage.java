package com.museotek.box.infrastructure.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Media files on local disk, same approach as Catalogue's FileStorageService: {@code ./uploads}
 * locally, a Docker volume mounted at {@code /app/uploads} in deployment. Laid out as
 * {@code {orgId}/{random UUID}{extension}} under {@code museotek.media.upload-dir}.
 *
 * <p>Works only while the backend runs as one instance: a second instance wouldn't see the first
 * one's disk. That is the point to move to object storage.
 */
@Component
public class LocalDiskMediaStorage implements MediaStorage {

    private static final Pattern EXTENSION = Pattern.compile("^\\.[a-z0-9]{1,5}$");

    private final Path root;

    public LocalDiskMediaStorage(MediaProperties properties) {
        this.root = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media upload directory: " + root, e);
        }
    }

    @Override
    public String store(UUID orgId, String extension, InputStream content) {
        if (extension == null || !EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Not a safe file extension: " + extension);
        }
        String storedFilename = orgId + "/" + UUID.randomUUID() + extension;
        Path target = safeResolve(storedFilename);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store media file " + storedFilename, e);
        }
        return storedFilename;
    }

    @Override
    public Resource load(String storedFilename) {
        Resource resource = new FileSystemResource(safeResolve(storedFilename));
        if (!resource.exists() || !resource.isReadable()) {
            return null;
        }
        return resource;
    }

    @Override
    public void delete(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(safeResolve(storedFilename));
        } catch (IOException ignored) {
            // Best-effort: an orphaned file is preferable to failing the user's request.
        }
    }

    // Path traversal guard: whatever the name, the resolved path must stay inside the root.
    private Path safeResolve(String storedFilename) {
        Path target = root.resolve(storedFilename).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the media upload directory: " + storedFilename);
        }
        return target;
    }
}
