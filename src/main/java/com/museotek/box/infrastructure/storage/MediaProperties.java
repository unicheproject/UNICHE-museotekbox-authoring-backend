package com.museotek.box.infrastructure.storage;

import com.museotek.box.domain.media.MediaKind;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Media library settings ({@code museotek.media.*}). Defaults live in application.properties only.
 *
 * @param uploadDir where files are written: {@code ./uploads} locally, a Docker volume in deployment
 * @param kinds     per kind, the largest file accepted and the content types allowed
 */
@ConfigurationProperties("museotek.media")
public record MediaProperties(String uploadDir, Map<MediaKind, KindRule> kinds) {

    public record KindRule(long maxBytes, List<String> contentTypes) {
    }

    /** The kind whose allowed types include this content type, or {@code null} if none does. */
    public MediaKind kindOf(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (Map.Entry<MediaKind, KindRule> entry : kinds.entrySet()) {
            if (entry.getValue().contentTypes().contains(contentType)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
