package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Removes a media item and its file. Any member who can reach the org may delete, like upload.
 * Not yet refused while an experience uses the item: that needs experiences to reference media
 * by id (see docs/proposal-media-library.md, open decisions 1 and 2).
 */
@Service
public class DeleteMediaUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;

    public DeleteMediaUseCase(OrgAccessGuard orgAccessGuard, MediaRepository mediaRepository, MediaStorage mediaStorage) {
        this.orgAccessGuard = orgAccessGuard;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
    }

    public void execute(UUID orgId, UUID mediaId) {
        orgAccessGuard.requireAccess(orgId);

        Media media = mediaRepository.findByIdAndOrgId(mediaId, orgId)
                .orElseThrow(() -> new MediaNotFoundException("No media " + mediaId + " for org " + orgId));

        // Row first, then the file: if the file delete fails, an orphan file is harmless,
        // while a row pointing at a deleted file would break every picker that lists it.
        mediaRepository.delete(media);
        mediaStorage.delete(media.getStoredFilename());
    }
}
