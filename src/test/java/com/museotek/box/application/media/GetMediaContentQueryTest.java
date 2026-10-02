package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetMediaContentQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final GetMediaContentQuery query = new GetMediaContentQuery(orgAccessGuard, mediaRepository, mediaStorage);

    private final UUID orgId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();

    @Test
    void success_returnsTheItemAndItsFile() {
        Media media = media();
        Resource file = new ByteArrayResource(new byte[]{1, 2, 3});
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.of(media));
        when(mediaStorage.load("stored.png")).thenReturn(file);

        GetMediaContentQuery.MediaContent content = query.execute(orgId, mediaId);

        assertThat(content.media()).isSameAs(media);
        assertThat(content.file()).isSameAs(file);
    }

    @Test
    void notInThisOrg_isNotFound() {
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(orgId, mediaId)).isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void fileMissingFromStorage_isNotFound() {
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.of(media()));
        when(mediaStorage.load("stored.png")).thenReturn(null);

        assertThatThrownBy(() -> query.execute(orgId, mediaId))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining("missing from storage");
    }

    @Test
    void noOrgAccess_touchesNothing() {
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId, mediaId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(mediaRepository, mediaStorage);
    }

    private Media media() {
        Media media = new Media();
        media.setId(mediaId);
        media.setOrgId(orgId);
        media.setStoredFilename("stored.png");
        return media;
    }
}
