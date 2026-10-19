package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Changes the name a media item shows in the picker. Any member who can reach the org may rename,
 * like upload and delete. Only the label changes: the file and the id stay, so experiences using
 * the item are unaffected.
 */
@Service
public class RenameMediaUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final MediaRepository mediaRepository;

    public RenameMediaUseCase(OrgAccessGuard orgAccessGuard, MediaRepository mediaRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.mediaRepository = mediaRepository;
    }

    public Media execute(UUID orgId, UUID mediaId, String name) {
        orgAccessGuard.requireAccess(orgId);
        String validName = MediaNames.requireValid(name);

        Media media = mediaRepository.findByIdAndOrgId(mediaId, orgId)
                .orElseThrow(() -> new MediaNotFoundException("No media " + mediaId + " for org " + orgId));
        if (validName.equals(media.getName())) {
            return media;
        }
        media.setName(validName);
        return mediaRepository.save(media);
    }
}
