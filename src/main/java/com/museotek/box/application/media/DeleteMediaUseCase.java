package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaInUseException;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.BlockRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Removes a media item and its file. Any member who can reach the org may delete, like upload.
 * Refused while any experience still uses the item (a block's mediaId or a reply effect's media
 * fields), since the Box would then show nothing there.
 */
@Service
public class DeleteMediaUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final BlockRepository blockRepository;
    private final RuleRepository ruleRepository;

    public DeleteMediaUseCase(
            OrgAccessGuard orgAccessGuard,
            MediaRepository mediaRepository,
            MediaStorage mediaStorage,
            BlockRepository blockRepository,
            RuleRepository ruleRepository
    ) {
        this.orgAccessGuard = orgAccessGuard;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
        this.blockRepository = blockRepository;
        this.ruleRepository = ruleRepository;
    }

    public void execute(UUID orgId, UUID mediaId) {
        orgAccessGuard.requireAccess(orgId);

        Media media = mediaRepository.findByIdAndOrgId(mediaId, orgId)
                .orElseThrow(() -> new MediaNotFoundException("No media " + mediaId + " for org " + orgId));
        if (blockRepository.existsByMediaId(mediaId.toString()) || ruleRepository.existsByEffectMediaId(mediaId.toString())) {
            throw new MediaInUseException("Media " + mediaId + " is still used by an experience");
        }

        // Row first, then the file: if the file delete fails, an orphan file is harmless,
        // while a row pointing at a deleted file would break every picker that lists it.
        mediaRepository.delete(media);
        mediaStorage.delete(media.getStoredFilename());
    }
}
