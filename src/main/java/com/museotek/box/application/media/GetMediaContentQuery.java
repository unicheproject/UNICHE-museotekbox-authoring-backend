package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** A media item's file, for previews in the editor (and, once the Box device API exists, for the Box). */
@Service
public class GetMediaContentQuery {

    public record MediaContent(Media media, Resource file) {
    }

    private final OrgAccessGuard orgAccessGuard;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;

    public GetMediaContentQuery(OrgAccessGuard orgAccessGuard, MediaRepository mediaRepository, MediaStorage mediaStorage) {
        this.orgAccessGuard = orgAccessGuard;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
    }

    public MediaContent execute(UUID orgId, UUID mediaId) {
        orgAccessGuard.requireAccess(orgId);

        Media media = mediaRepository.findByIdAndOrgId(mediaId, orgId)
                .orElseThrow(() -> new MediaNotFoundException("No media " + mediaId + " for org " + orgId));
        Resource file = mediaStorage.load(media.getStoredFilename());
        if (file == null) {
            throw new MediaNotFoundException("The file of media " + mediaId + " is missing from storage");
        }
        return new MediaContent(media, file);
    }
}
