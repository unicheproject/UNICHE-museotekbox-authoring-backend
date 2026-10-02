package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.InvalidMediaUploadException;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.domain.media.MediaTooLargeException;
import com.museotek.box.domain.media.UnsupportedMediaFileException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.storage.MediaProperties;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadMediaUseCaseTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final MediaProperties properties = new MediaProperties("uploads", Map.of(
            MediaKind.IMAGE, new MediaProperties.KindRule(100, List.of("image/png", "image/jpeg")),
            MediaKind.VIDEO, new MediaProperties.KindRule(1000, List.of("video/mp4"))));
    private final UploadMediaUseCase useCase = new UploadMediaUseCase(orgAccessGuard, mediaRepository, mediaStorage, properties);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void success_storesTheWholeFileAndSavesItsRow() throws Exception {
        when(mediaStorage.store(eq(orgId), eq(".png"), any())).thenAnswer(invocation -> {
            InputStream content = invocation.getArgument(2);
            assertThat(content.readAllBytes()).isEqualTo(PNG); // the type check must not swallow the first bytes
            return orgId + "/stored.png";
        });
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Media media = useCase.execute(orgId, "C:\\fakepath\\Gallery.png", "image/png", PNG.length, new ByteArrayInputStream(PNG));

        assertThat(media.getId()).isNotNull();
        assertThat(media.getOrgId()).isEqualTo(orgId);
        assertThat(media.getKind()).isEqualTo(MediaKind.IMAGE);
        assertThat(media.getName()).isEqualTo("Gallery.png");
        assertThat(media.getContentType()).isEqualTo("image/png");
        assertThat(media.getSizeBytes()).isEqualTo(PNG.length);
        assertThat(media.getStoredFilename()).isEqualTo(orgId + "/stored.png");
        assertThat(media.getUploadedAt()).isNotNull();
    }

    @Test
    void emptyFile_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, "x.png", "image/png", 0, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(InvalidMediaUploadException.class);

        verifyNoInteractions(mediaStorage, mediaRepository);
    }

    @Test
    void typeNotAllowed_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, "x.svg", "image/svg+xml", 10, new ByteArrayInputStream(new byte[10])))
                .isInstanceOf(UnsupportedMediaFileException.class)
                .hasMessageContaining("not allowed")
                .hasMessageContaining("image/jpeg, image/png, video/mp4");

        verifyNoInteractions(mediaStorage, mediaRepository);
    }

    @Test
    void tooLargeForItsKind_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, "x.png", "image/png", 101, new ByteArrayInputStream(PNG)))
                .isInstanceOf(MediaTooLargeException.class)
                .hasMessageContaining("at most 100 bytes");

        verifyNoInteractions(mediaStorage, mediaRepository);
    }

    @Test
    void contentNotMatchingItsType_isRefused() {
        byte[] script = "<script>alert(1)</script>".getBytes();

        assertThatThrownBy(() -> useCase.execute(orgId, "photo.png", "image/png", script.length, new ByteArrayInputStream(script)))
                .isInstanceOf(UnsupportedMediaFileException.class)
                .hasMessageContaining("doesn't match");

        verifyNoInteractions(mediaStorage, mediaRepository);
    }

    @Test
    void databaseFailure_removesTheStoredFile() {
        when(mediaStorage.store(eq(orgId), eq(".png"), any())).thenReturn(orgId + "/stored.png");
        when(mediaRepository.save(any(Media.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> useCase.execute(orgId, "x.png", "image/png", PNG.length, new ByteArrayInputStream(PNG)))
                .hasMessage("db down");

        verify(mediaStorage).delete(orgId + "/stored.png");
    }

    @Test
    void noOrgAccess_touchesNothing() {
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, "x.png", "image/png", PNG.length, new ByteArrayInputStream(PNG)))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(mediaStorage, mediaRepository);
    }

    @Test
    void displayName_keepsOnlyTheFilename() {
        assertThat(UploadMediaUseCase.displayName("folder/sub/vase.jpg")).isEqualTo("vase.jpg");
        assertThat(UploadMediaUseCase.displayName("  ")).isEqualTo("upload");
        assertThat(UploadMediaUseCase.displayName(null)).isEqualTo("upload");
        assertThat(UploadMediaUseCase.displayName("a".repeat(300))).hasSize(255);
    }
}
