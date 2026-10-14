package com.museotek.box.application.box;

import com.museotek.box.application.experience.ExperienceGaps;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.ExperienceNotReadyException;
import com.museotek.box.domain.box.ProjectNotInBoxOrgException;
import com.museotek.box.domain.box.StartCardInUseException;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssignProjectToBoxUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final SceneRepository sceneRepository = mock(SceneRepository.class);
    private final ExperienceGaps experienceGaps = mock(ExperienceGaps.class);
    private final BoxStartCards boxStartCards = mock(BoxStartCards.class);
    private final AssignProjectToBoxUseCase useCase = new AssignProjectToBoxUseCase(
            orgAccessGuard, projectAccessGuard, boxRepository, projectRepository, sceneRepository, experienceGaps, boxStartCards);

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    void success_addsProjectToBox() {
        Box box = new Box();
        Project project = projectWithId(projectId);
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject(orgId));
        when(projectRepository.getReferenceById(projectId)).thenReturn(project);
        when(experienceGaps.find(projectId)).thenReturn(List.of());
        when(sceneRepository.findByProjectIdAndStartTrue(projectId)).thenReturn(Optional.of(startSceneWithCard(7L)));

        useCase.execute(orgId, 1L, projectId);

        assertThat(box.getAssignedProjects()).containsExactly(project);
        verify(boxStartCards).requireFreeOnBox(1L, projectId, 7L);
    }

    @Test
    void alreadyAssigned_isANoOp_withoutCheckingAgain() {
        Box box = new Box();
        Project project = projectWithId(projectId);
        box.getAssignedProjects().add(project);
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(boxRepository.findAssignedProjectIds(1L)).thenReturn(List.of(projectId));
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject(orgId));

        useCase.execute(orgId, 1L, projectId);

        assertThat(box.getAssignedProjects()).containsExactly(project);
        verifyNoInteractions(experienceGaps, boxStartCards);
    }

    @Test
    void experienceWithGaps_isRefusedWithTheGaps() {
        Box box = new Box();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject(orgId));
        when(experienceGaps.find(projectId)).thenReturn(List.of("scene '1' (Start) is the start scene but has no start card"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, projectId))
                .isInstanceOf(ExperienceNotReadyException.class)
                .extracting(e -> ((ExperienceNotReadyException) e).getGaps())
                .isEqualTo(List.of("scene '1' (Start) is the start scene but has no start card"));

        assertThat(box.getAssignedProjects()).isEmpty();
        verifyNoInteractions(boxStartCards);
    }

    @Test
    void startCardAlreadyUsedOnTheBox_isRefused() {
        Box box = new Box();
        when(boxRepository.findByIdAndOrgId(1L, orgId)).thenReturn(Optional.of(box));
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject(orgId));
        when(experienceGaps.find(projectId)).thenReturn(List.of());
        when(sceneRepository.findByProjectIdAndStartTrue(projectId)).thenReturn(Optional.of(startSceneWithCard(7L)));
        doThrow(new StartCardInUseException("taken")).when(boxStartCards).requireFreeOnBox(1L, projectId, 7L);

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, projectId)).isInstanceOf(StartCardInUseException.class);

        assertThat(box.getAssignedProjects()).isEmpty();
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
    void notOrgManager_neverTouchesRepository() {
        when(orgAccessGuard.requireManager(orgId)).thenThrow(new OrgManagerRequiredException("not a manager"));

        assertThatThrownBy(() -> useCase.execute(orgId, 1L, projectId)).isInstanceOf(OrgManagerRequiredException.class);

        verifyNoInteractions(boxRepository);
    }

    private static Scene startSceneWithCard(Long cardTypeId) {
        ScanObjectType card = new ScanObjectType();
        card.setId(cardTypeId);
        Scene scene = new Scene();
        scene.setStart(true);
        scene.setInitCardType(card);
        return scene;
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
