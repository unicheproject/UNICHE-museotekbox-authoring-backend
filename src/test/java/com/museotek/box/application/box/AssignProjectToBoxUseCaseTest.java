package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.ProjectNotInBoxOrgException;
import com.museotek.box.domain.project.Project;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssignProjectToBoxUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final AssignProjectToBoxUseCase useCase =
            new AssignProjectToBoxUseCase(orgAccessGuard, projectAccessGuard, boxRepository, projectRepository);

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    void success_addsProjectToBox() {
        Box box = new Box();
        Project project = projectWithId(projectId);
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject(orgId));
        when(projectRepository.getReferenceById(projectId)).thenReturn(project);

        useCase.execute(orgId, 1L, projectId);

        assertThat(box.getAssignedProjects()).containsExactly(project);
    }

    @Test
    void alreadyAssigned_isANoOp() {
        Box box = new Box();
        Project project = projectWithId(projectId);
        box.getAssignedProjects().add(project);
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject(orgId));
        when(projectRepository.getReferenceById(projectId)).thenReturn(project);

        useCase.execute(orgId, 1L, projectId);

        assertThat(box.getAssignedProjects()).containsExactly(project);
    }

    @Test
    void projectFromAnotherOrg_throwsAndAssignsNothing() {
        Box box = new Box();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject(UUID.randomUUID()));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, projectId)).isInstanceOf(ProjectNotInBoxOrgException.class);

        assertThat(box.getAssignedProjects()).isEmpty();
    }

    @Test
    void projectAccessDenied_assignsNothing() {
        Box box = new Box();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, projectId)).isInstanceOf(CatalogueForbiddenException.class);

        assertThat(box.getAssignedProjects()).isEmpty();
        verifyNoInteractions(projectRepository);
    }

    @Test
    void boxNotFoundForOrg_neverChecksProject() {
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

    private CatalogueProjectDto catalogueProject(UUID projectOrgId) {
        return new CatalogueProjectDto(
                projectId.toString(), projectOrgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
    }
}
