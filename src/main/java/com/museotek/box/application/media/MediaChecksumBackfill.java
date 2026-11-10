package com.museotek.box.application.media;

import com.museotek.box.domain.media.Media;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fills in {@code media.sha256} for files uploaded before the column existed. Runs once the app is up, so it doesn't
 * hold back startup; does nothing when every row has one. A missing file is logged and skipped (stays null).
 * Two instances running it at once is harmless: both write the same value.
 */
@Component
public class MediaChecksumBackfill {

    private static final Logger log = LoggerFactory.getLogger(MediaChecksumBackfill.class);

    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;

    public MediaChecksumBackfill(MediaRepository mediaRepository, MediaStorage mediaStorage) {
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void fillMissing() {
        List<Media> missing = mediaRepository.findBySha256IsNull();
        if (missing.isEmpty()) {
            return;
        }
        log.info("Calculating SHA-256 for {} media files uploaded before it was stored", missing.size());
        for (Media media : missing) {
            Resource file = mediaStorage.load(media.getStoredFilename());
            if (file == null) {
                log.warn("Media {} has no file in storage; its SHA-256 stays empty", media.getId());
                continue;
            }
            media.setSha256(MediaChecksums.sha256Of(file));
            mediaRepository.save(media);
        }
    }
}
