package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListBoxProjectsQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final ListBoxProjectsQuery query = new ListBoxProjectsQuery(orgAccessGuard, boxRepository, catalogueClient);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void success_returnsOnlyAssignedProjectsTheCallerCanSee() {
        Box box = boxWithId(1L);
        UUID assignedVisible = UUID.randomUUID();
        UUID assignedHidden = UUID.randomUUID();
        UUID visibleNotAssigned = UUID.randomUUID();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(boxRepository.findAssignedProjectIds(1L)).thenReturn(List.of(assignedVisible, assignedHidden));
        // Catalogue already filters per caller: an author doesn't get assignedHidden back.
        CatalogueProjectDto visible = project(assignedVisible);
        when(catalogueClient.listProjectsForOrg(orgId)).thenReturn(List.of(visible, project(visibleNotAssigned)));

        List<CatalogueProjectDto> result = query.execute(orgId, 1L);

        assertThat(result).containsExactly(visible);
    }

    @Test
    void nothingAssigned_returnsEmptyWithoutCallingCatalogueForProjects() {
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(boxWithId(1L)));
        when(boxRepository.findAssignedProjectIds(1L)).thenReturn(List.of());

        List<CatalogueProjectDto> result = query.execute(orgId, 1L);

        assertThat(result).isEmpty();
        verifyNoInteractions(catalogueClient);
    }

    @Test
    void boxNotFoundForOrg_throws() {
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(orgId, 1L)).isInstanceOf(BoxNotFoundException.class);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId, 1L)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(boxRepository);
    }

    private static Box boxWithId(Long id) {
        Box box = new Box();
        box.setId(id);
        return box;
    }

    private CatalogueProjectDto project(UUID id) {
        return new CatalogueProjectDto(
                id.toString(), orgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
    }
}
