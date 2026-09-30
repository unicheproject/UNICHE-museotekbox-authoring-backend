package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.project.Project;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnassignProjectFromBoxUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final UnassignProjectFromBoxUseCase useCase =
            new UnassignProjectFromBoxUseCase(orgAccessGuard, projectAccessGuard, boxRepository);

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    void success_removesOnlyThatProject() {
        Box box = new Box();
        Project other = projectWithId(UUID.randomUUID());
        box.getAssignedProjects().add(projectWithId(projectId));
        box.getAssignedProjects().add(other);
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));

        useCase.execute(orgId, 1L, projectId);

        assertThat(box.getAssignedProjects()).containsExactly(other);
    }

    @Test
    void notAssigned_isANoOp() {
        Box box = new Box();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));

        useCase.execute(orgId, 1L, projectId);

        assertThat(box.getAssignedProjects()).isEmpty();
    }

    @Test
    void projectAccessDenied_removesNothing() {
        Box box = new Box();
        box.getAssignedProjects().add(projectWithId(projectId));
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, projectId)).isInstanceOf(CatalogueForbiddenException.class);

        assertThat(box.getAssignedProjects()).hasSize(1);
    }

    @Test
    void boxNotFoundForOrg_throws() {
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, projectId)).isInstanceOf(BoxNotFoundException.class);

        verifyNoInteractions(projectAccessGuard);
    }

    @Test
    void orgAccessDenied_neverTouchesRepository() {
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, projectId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(boxRepository);
    }

    private static Project projectWithId(UUID id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }
}
