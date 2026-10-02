package com.museotek.box.application.media;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.MediaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListMediaQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final ListMediaQuery query = new ListMediaQuery(orgAccessGuard, mediaRepository);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void withoutKind_returnsAllOfTheOrg() {
        Media media = new Media();
        when(mediaRepository.findByOrgIdOrderByUploadedAtDesc(orgId)).thenReturn(List.of(media));

        assertThat(query.execute(orgId, null)).containsExactly(media);
    }

    @Test
    void withKind_returnsOnlyThatKind() {
        Media image = new Media();
        when(mediaRepository.findByOrgIdAndKindOrderByUploadedAtDesc(orgId, MediaKind.IMAGE)).thenReturn(List.of(image));

        assertThat(query.execute(orgId, MediaKind.IMAGE)).containsExactly(image);
    }

    @Test
    void noOrgAccess_neverTouchesRepository() {
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId, null)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(mediaRepository);
    }
}
