package com.museotek.box.application.media;

import com.museotek.box.domain.media.Media;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaChecksumBackfillTest {

    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final MediaChecksumBackfill backfill = new MediaChecksumBackfill(mediaRepository, mediaStorage);

    @Test
    void aFileWithoutAHash_getsOne() {
        Media media = media("org/a.png");
        when(mediaRepository.findBySha256IsNull()).thenReturn(List.of(media));
        when(mediaStorage.load("org/a.png")).thenReturn(new ByteArrayResource("abc".getBytes(StandardCharsets.US_ASCII)));

        backfill.fillMissing();

        assertThat(media.getSha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        verify(mediaRepository).save(media);
    }

    @Test
    void aMissingFile_isSkipped() {
        Media media = media("org/gone.png");
        when(mediaRepository.findBySha256IsNull()).thenReturn(List.of(media));
        when(mediaStorage.load("org/gone.png")).thenReturn(null);

        backfill.fillMissing();

        assertThat(media.getSha256()).isNull();
        verify(mediaRepository, never()).save(any());
    }

    @Test
    void nothingMissing_readsNoFiles() {
        when(mediaRepository.findBySha256IsNull()).thenReturn(List.of());

        backfill.fillMissing();

        verifyNoInteractions(mediaStorage);
    }

    private static Media media(String storedFilename) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setStoredFilename(storedFilename);
        return media;
    }
}
