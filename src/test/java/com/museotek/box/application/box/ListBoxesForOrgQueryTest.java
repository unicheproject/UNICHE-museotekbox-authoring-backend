package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListBoxesForOrgQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ListBoxesForOrgQuery query = new ListBoxesForOrgQuery(orgAccessGuard, boxRepository);

    @Test
    void success_returnsOrgsBoxes() {
        UUID orgId = UUID.randomUUID();
        Box box = new Box();
        when(boxRepository.findByOrgId(orgId)).thenReturn(List.of(box));

        List<Box> result = query.execute(orgId);

        assertThat(result).containsExactly(box);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        UUID orgId = UUID.randomUUID();
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(boxRepository);
    }
}
