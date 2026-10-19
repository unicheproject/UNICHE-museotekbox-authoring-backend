package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.InvalidMediaNameException;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RenameMediaUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final RenameMediaUseCase useCase = new RenameMediaUseCase(orgAccessGuard, mediaRepository);

    private final UUID orgId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();

    @Test
    void success_savesTheTrimmedName_andKeepsTheFile() {
        Media media = media("IMG_0042.jpg");
        when(mediaRepository.save(media)).thenReturn(media);

        Media renamed = useCase.execute(orgId, mediaId, "  Room 1/2 entrance  ");

        assertThat(renamed.getName()).isEqualTo("Room 1/2 entrance");
        assertThat(renamed.getStoredFilename()).isEqualTo("stored.jpg");
        verify(mediaRepository).save(media);
    }

    @Test
    void sameName_savesNothing() {
        media("Gallery");

        assertThat(useCase.execute(orgId, mediaId, "Gallery").getName()).isEqualTo("Gallery");

        verify(mediaRepository, never()).save(any());
    }

    @Test
    void blankOrMissingName_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, mediaId, "  ")).isInstanceOf(InvalidMediaNameException.class);
        assertThatThrownBy(() -> useCase.execute(orgId, mediaId, null)).isInstanceOf(InvalidMediaNameException.class);

        verifyNoInteractions(mediaRepository);
    }

    @Test
    void nameTooLong_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, mediaId, "a".repeat(256))).isInstanceOf(InvalidMediaNameException.class);
        assertThat(MediaNames.requireValid("a".repeat(255))).hasSize(255);
    }

    @Test
    void mediaOfAnotherOrg_isNotFound() {
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, mediaId, "x")).isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void noOrgAccess_touchesNothing() {
        doThrow(new CatalogueForbiddenException("no")).when(orgAccessGuard).requireAccess(orgId);

        assertThatThrownBy(() -> useCase.execute(orgId, mediaId, "x")).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(mediaRepository);
    }

    private Media media(String name) {
        Media media = new Media();
        media.setId(mediaId);
        media.setOrgId(orgId);
        media.setName(name);
        media.setStoredFilename("stored.jpg");
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.of(media));
        return media;
    }
}
