package com.museotek.box.application.player;

import com.museotek.box.application.media.GetMediaContentQuery.MediaContent;
import com.museotek.box.application.player.DisplayMediaLinks.SignedLink;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.domain.display.InvalidMediaLinkException;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * A media file for a display, through a signed link. Checks, in order: the tool signed the link and it hasn't expired;
 * its display session is still active; and the media belongs to the latest copy of an experience on that display's
 * Box. Doesn't move the session's expiry: loading media is not the display being used.
 */
@Service
public class GetDisplayMediaQuery {

    /** The file, and until when the link is valid (so the browser caches it no longer). */
    public record DisplayMedia(MediaContent content, Instant linkExpiresAt) {
    }

    private final DisplayMediaLinks links;
    private final DisplaySessionRepository sessionRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final Clock clock;

    public GetDisplayMediaQuery(DisplayMediaLinks links, DisplaySessionRepository sessionRepository,
                                PublishedExperienceRepository publishedExperienceRepository,
                                MediaRepository mediaRepository, MediaStorage mediaStorage, Clock clock) {
        this.links = links;
        this.sessionRepository = sessionRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
        this.clock = clock;
    }

    public DisplayMedia execute(SignedLink link) {
        if (!links.isValid(link)) {
            throw new InvalidMediaLinkException("Wrong or expired media link");
        }
        DisplaySession session = sessionRepository.findById(link.sessionId())
                .filter(s -> s.getRevokedAt() == null && s.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new InvalidMediaLinkException("The display of this media link was revoked"));
        if (!publishedExperienceRepository.latestCopyOnBoxUsesMedia(session.getBoxId(), link.mediaId().toString())) {
            throw new MediaNotFoundException("No media " + link.mediaId() + " on box " + session.getBoxId());
        }
        Media media = mediaRepository.findById(link.mediaId())
                .orElseThrow(() -> new MediaNotFoundException("No media " + link.mediaId()));
        Resource file = mediaStorage.load(media.getStoredFilename());
        if (file == null) {
            throw new MediaNotFoundException("The file of media " + link.mediaId() + " is missing from storage");
        }
        return new DisplayMedia(new MediaContent(media, file), Instant.ofEpochSecond(link.expires()));
    }
}
