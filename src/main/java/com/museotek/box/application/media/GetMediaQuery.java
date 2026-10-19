package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** One media item's details, e.g. to show the name of the file a block uses without loading the whole library. */
@Service
public class GetMediaQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final MediaRepository mediaRepository;

    public GetMediaQuery(OrgAccessGuard orgAccessGuard, MediaRepository mediaRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.mediaRepository = mediaRepository;
    }

    public Media execute(UUID orgId, UUID mediaId) {
        orgAccessGuard.requireAccess(orgId);
        return mediaRepository.findByIdAndOrgId(mediaId, orgId)
                .orElseThrow(() -> new MediaNotFoundException("No media " + mediaId + " for org " + orgId));
    }
}
