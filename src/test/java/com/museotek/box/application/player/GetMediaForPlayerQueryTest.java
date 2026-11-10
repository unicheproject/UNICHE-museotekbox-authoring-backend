package com.museotek.box.application.player;

import com.museotek.box.application.media.GetMediaContentQuery.MediaContent;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetMediaForPlayerQueryTest {

    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final GetMediaForPlayerQuery query = new GetMediaForPlayerQuery(mediaRepository, mediaStorage);

    private final UUID mediaId = UUID.randomUUID();

    @Test
    void aKnownMedia_comesWithItsFile() {
        Media media = media();
        Resource file = new ByteArrayResource(new byte[]{1});
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media));
        when(mediaStorage.load("org/a.mp4")).thenReturn(file);

        MediaContent content = query.execute(mediaId);

        assertThat(content.media()).isSameAs(media);
        assertThat(content.file()).isSameAs(file);
    }

    @Test
    void anUnknownMedia_throws() {
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(mediaId)).isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void aMissingFile_throws() {
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(media()));
        when(mediaStorage.load("org/a.mp4")).thenReturn(null);

        assertThatThrownBy(() -> query.execute(mediaId)).isInstanceOf(MediaNotFoundException.class);
    }

    private Media media() {
        Media media = new Media();
        media.setId(mediaId);
        media.setStoredFilename("org/a.mp4");
        return media;
    }
}
