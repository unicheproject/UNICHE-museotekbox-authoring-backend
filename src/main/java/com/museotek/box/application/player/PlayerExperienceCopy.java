package com.museotek.box.application.player;

import com.museotek.box.domain.media.Media;

import java.time.Instant;
import java.util.List;

/** One experience's latest published copy as the player project plays it: the content and the media files it needs. */
public record PlayerExperienceCopy(PlayerExperience experience, Instant publishedAt, String content, List<Media> media) {

    public PlayerExperienceCopy {
        media = List.copyOf(media);
    }
}
