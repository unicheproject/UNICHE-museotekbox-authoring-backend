package com.museotek.box.infrastructure.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.UUID;

/**
 * Where media library files live. Only {@link LocalDiskMediaStorage} today; moving to object
 * storage (Azure Blob, S3-compatible) means another implementation, nothing else.
 *
 * <p>Files are addressed by a stored filename this class hands out, never by the uploader's
 * original filename, so a client-chosen name can't reach outside the storage area.
 */
public interface MediaStorage {

    /** Writes the content under a new, unique name and returns that stored filename. */
    String store(UUID orgId, String extension, InputStream content);

    /** The file for serving, or {@code null} if it doesn't exist (any more). */
    Resource load(String storedFilename);

    /** Best-effort: a file that's already gone is not an error. */
    void delete(String storedFilename);
}
