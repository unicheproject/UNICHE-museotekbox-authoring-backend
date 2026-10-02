package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.infrastructure.repository.MediaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** The org's media library, newest first, optionally one kind (what a picker shows). */
@Service
public class ListMediaQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final MediaRepository mediaRepository;

    public ListMediaQuery(OrgAccessGuard orgAccessGuard, MediaRepository mediaRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.mediaRepository = mediaRepository;
    }

    public List<Media> execute(UUID orgId, MediaKind kind) {
        orgAccessGuard.requireAccess(orgId);
        if (kind == null) {
            return mediaRepository.findByOrgIdOrderByUploadedAtDesc(orgId);
        }
        return mediaRepository.findByOrgIdAndKindOrderByUploadedAtDesc(orgId, kind);
    }
}
