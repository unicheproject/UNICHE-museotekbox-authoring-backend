package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetMediaQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final GetMediaQuery query = new GetMediaQuery(orgAccessGuard, mediaRepository);

    private final UUID orgId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();

    @Test
    void returnsTheOrgsItem() {
        Media media = new Media();
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.of(media));

        assertThat(query.execute(orgId, mediaId)).isSameAs(media);
    }

    @Test
    void mediaOfAnotherOrg_isNotFound() {
        when(mediaRepository.findByIdAndOrgId(mediaId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(orgId, mediaId)).isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    void noOrgAccess_touchesNothing() {
        doThrow(new CatalogueForbiddenException("no")).when(orgAccessGuard).requireAccess(orgId);

        assertThatThrownBy(() -> query.execute(orgId, mediaId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(mediaRepository);
    }
}
