package com.museotek.box.application.player;

import com.museotek.box.application.media.GetMediaContentQuery.MediaContent;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A media file for the player project, by id. No org in the path: the player project's secret already lets it read
 * every museum's Boxes and copies, and it learns media ids from those copies.
 */
@Service
public class GetMediaForPlayerQuery {

    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;

    public GetMediaForPlayerQuery(MediaRepository mediaRepository, MediaStorage mediaStorage) {
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
    }

    public MediaContent execute(UUID mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("No media " + mediaId));
        Resource file = mediaStorage.load(media.getStoredFilename());
        if (file == null) {
            throw new MediaNotFoundException("The file of media " + mediaId + " is missing from storage");
        }
        return new MediaContent(media, file);
    }
}
