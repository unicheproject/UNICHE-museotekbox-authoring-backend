package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.InvalidMediaUploadException;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.domain.media.MediaTooLargeException;
import com.museotek.box.domain.media.UnsupportedMediaFileException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.security.CurrentPrincipal;
import com.museotek.box.infrastructure.storage.MediaProperties;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adds one file to an organisation's media library. Any member who can reach the org may upload:
 * authors build experiences, so they need their own pictures and sounds.
 *
 * <p>The name shown in the picker is the one given, or else the file's own name.
 *
 * <p>Checks, in order: access, a valid name if one is given, not empty, allowed type, within its
 * kind's size limit, and the file's first bytes really are that type. Then the file is written and its row saved; if the
 * row fails, the file is removed again so no orphan is left on disk.
 */
@Service
public class UploadMediaUseCase {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/webp", ".webp",
            "audio/mpeg", ".mp3",
            "audio/wav", ".wav",
            "audio/ogg", ".ogg",
            "video/mp4", ".mp4");

    private final OrgAccessGuard orgAccessGuard;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final MediaProperties mediaProperties;
    private final Clock clock;

    public UploadMediaUseCase(
            OrgAccessGuard orgAccessGuard,
            MediaRepository mediaRepository,
            MediaStorage mediaStorage,
            MediaProperties mediaProperties,
            Clock clock
    ) {
        this.orgAccessGuard = orgAccessGuard;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
        this.mediaProperties = mediaProperties;
        this.clock = clock;
    }

    public Media execute(
            UUID orgId, String originalFilename, String name, String contentType, long sizeBytes, InputStream content
    ) {
        orgAccessGuard.requireAccess(orgId);
        String shownName = name == null || name.isBlank() ? displayName(originalFilename) : MediaNames.requireValid(name);

        if (sizeBytes <= 0) {
            throw new InvalidMediaUploadException("No file was uploaded, or the file is empty");
        }
        MediaKind kind = mediaProperties.kindOf(contentType);
        String extension = EXTENSIONS.get(contentType);
        if (kind == null || extension == null) {
            throw new UnsupportedMediaFileException("File type '" + contentType + "' is not allowed. Allowed: " + allowedTypes());
        }
        long maxBytes = mediaProperties.kinds().get(kind).maxBytes();
        if (sizeBytes > maxBytes) {
            throw new MediaTooLargeException("The file is " + sizeBytes + " bytes; " + kind + " files may be at most "
                    + maxBytes + " bytes");
        }

        BufferedInputStream buffered = new BufferedInputStream(content);
        if (!MediaFileSignatures.matches(contentType, peekHeader(buffered))) {
            throw new UnsupportedMediaFileException("The file's content doesn't match its declared type '" + contentType + "'");
        }

        String storedFilename = mediaStorage.store(orgId, extension, buffered);
        try {
            Media media = new Media();
            media.setId(UUID.randomUUID());
            media.setOrgId(orgId);
            media.setKind(kind);
            media.setName(shownName);
            media.setContentType(contentType);
            media.setSizeBytes(sizeBytes);
            media.setStoredFilename(storedFilename);
            media.setUploadedBy(CurrentPrincipal.subject().orElse(null));
            media.setUploadedAt(Instant.now(clock));
            return mediaRepository.save(media);
        } catch (RuntimeException e) {
            mediaStorage.delete(storedFilename);
            throw e;
        }
    }

    private String allowedTypes() {
        return mediaProperties.kinds().values().stream()
                .flatMap(rule -> rule.contentTypes().stream())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    // Reads the first bytes without consuming them, so the whole file still gets stored.
    private static byte[] peekHeader(BufferedInputStream in) {
        try {
            in.mark(MediaFileSignatures.HEADER_BYTES);
            byte[] header = in.readNBytes(MediaFileSignatures.HEADER_BYTES);
            in.reset();
            return header;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    // The browser's filename, without any folder part some browsers include ("C:\fakepath\x.jpg").
    static String displayName(String originalFilename) {
        if (originalFilename == null) {
            return "upload";
        }
        String name = originalFilename.substring(Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\')) + 1).trim();
        if (name.isEmpty()) {
            return "upload";
        }
        if (name.length() > MediaNames.MAX_LENGTH) {
            return name.substring(0, MediaNames.MAX_LENGTH);
        }
        return name;
    }
}
