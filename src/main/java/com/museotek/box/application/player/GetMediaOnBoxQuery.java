package com.museotek.box.application.player;

import com.museotek.box.application.media.GetMediaContentQuery.MediaContent;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A media file, only if the latest copy of an experience on this Box uses it. For the Box itself and for its display:
 * both are tied to one Box and must not reach any other media.
 */
@Service
public class GetMediaOnBoxQuery {

    private final PublishedExperienceRepository publishedExperienceRepository;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;

    public GetMediaOnBoxQuery(PublishedExperienceRepository publishedExperienceRepository,
                              MediaRepository mediaRepository, MediaStorage mediaStorage) {
        this.publishedExperienceRepository = publishedExperienceRepository;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
    }

    public MediaContent execute(Long boxId, UUID mediaId) {
        if (!publishedExperienceRepository.latestCopyOnBoxUsesMedia(boxId, mediaId.toString())) {
            throw new MediaNotFoundException("No media " + mediaId + " on box " + boxId);
        }
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("No media " + mediaId));
        Resource file = mediaStorage.load(media.getStoredFilename());
        if (file == null) {
            throw new MediaNotFoundException("The file of media " + mediaId + " is missing from storage");
        }
        return new MediaContent(media, file);
    }
}
