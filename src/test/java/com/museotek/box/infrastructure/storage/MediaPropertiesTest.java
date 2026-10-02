package com.museotek.box.infrastructure.storage;

import com.museotek.box.domain.media.MediaKind;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MediaPropertiesTest {

    // Same shape as application.properties: lower-case kind keys, comma-separated types.
    private final MediaProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "museotek.media.upload-dir", "uploads",
            "museotek.media.kinds.image.max-bytes", "10485760",
            "museotek.media.kinds.image.content-types", "image/png,image/jpeg,image/webp",
            "museotek.media.kinds.video.max-bytes", "209715200",
            "museotek.media.kinds.video.content-types", "video/mp4")))
            .bind("museotek.media", MediaProperties.class)
            .get();

    @Test
    void bindsKindsFromLowerCaseKeys() {
        assertThat(properties.uploadDir()).isEqualTo("uploads");
        assertThat(properties.kinds().get(MediaKind.IMAGE).maxBytes()).isEqualTo(10485760L);
        assertThat(properties.kinds().get(MediaKind.IMAGE).contentTypes()).containsExactly("image/png", "image/jpeg", "image/webp");
    }

    @Test
    void kindOf_findsTheKindAllowingThatType() {
        assertThat(properties.kindOf("video/mp4")).isEqualTo(MediaKind.VIDEO);
        assertThat(properties.kindOf("image/webp")).isEqualTo(MediaKind.IMAGE);
        assertThat(properties.kindOf("image/svg+xml")).isNull();
        assertThat(properties.kindOf(null)).isNull();
    }
}
