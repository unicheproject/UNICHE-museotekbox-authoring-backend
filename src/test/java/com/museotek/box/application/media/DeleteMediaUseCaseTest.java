package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaInUseException;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.BlockRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteMediaUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final RuleRepository ruleRepository = mock(RuleRepository.class);
    private final DeleteMediaUseCase useCase =
            new DeleteMediaUseCase(orgAccessGuard, mediaRepository, mediaStorage, blockRepository, ruleRepository);

    private final UUID orgId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();

    @Test
    void success_deletesTheRowThenTheFile() {
        Media media = new Media();
        media.setStoredFilename("stored.mp4");
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.of(media));

        useCase.execute(orgId, mediaId);

        InOrder order = inOrder(mediaRepository, mediaStorage);
        order.verify(mediaRepository).delete(media);
        order.verify(mediaStorage).delete("stored.mp4");
    }

    @Test
    void usedByABlock_isRefusedAndDeletesNothing() {
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.of(new Media()));
        when(blockRepository.existsByMediaId(mediaId.toString())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(orgId, mediaId)).isInstanceOf(MediaInUseException.class);

        verify(mediaRepository, never()).delete(any());
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void usedByAReplyEffect_isRefused() {
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.of(new Media()));
        when(ruleRepository.existsByEffectMediaId(mediaId.toString())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(orgId, mediaId)).isInstanceOf(MediaInUseException.class);
    }

    @Test
    void notInThisOrg_isNotFoundAndDeletesNothing() {
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, mediaId)).isInstanceOf(MediaNotFoundException.class);

        verify(mediaRepository, never()).delete(any());
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void noOrgAccess_touchesNothing() {
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, mediaId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(mediaRepository, mediaStorage);
    }
}
