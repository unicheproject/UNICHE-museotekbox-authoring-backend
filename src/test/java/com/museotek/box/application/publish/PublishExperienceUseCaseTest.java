package com.museotek.box.application.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.box.BoxStartCards;
import com.museotek.box.application.experience.ExperienceGaps;
import com.museotek.box.application.experience.ExperienceView;
import com.museotek.box.application.experience.GetExperienceQuery;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.block.BlockType;
import com.museotek.box.domain.box.ExperienceNotReadyException;
import com.museotek.box.domain.box.StartCardInUseException;
import com.museotek.box.domain.experience.ExperienceFlow;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

class PublishExperienceUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final PublishedExperienceRepository publishedExperienceRepository = mock(PublishedExperienceRepository.class);
    private final GetExperienceQuery getExperienceQuery = mock(GetExperienceQuery.class);
    private final ExperienceGaps experienceGaps = mock(ExperienceGaps.class);
    private final BoxStartCards boxStartCards = mock(BoxStartCards.class);
    private final PublishExperienceUseCase useCase = new PublishExperienceUseCase(
            projectAccessGuard, orgAccessGuard, projectRepository, publishedExperienceRepository, getExperienceQuery,
            experienceGaps, boxStartCards, new PublishedContentMapper(new ObjectMapper()), Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID video = UUID.randomUUID();
    private final Project project = project(5);

    @Test
    void firstPublish_savesVersion1_withTheCatalogueNameAndTheDraftsContent() {
        stubReadyDraft();
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.empty());

        PublishStatus status = useCase.execute(projectId);

        PublishedExperience copy = savedCopy();
        assertThat(copy.getVersion()).isEqualTo(1);
        assertThat(copy.getDocVersion()).isEqualTo(5);
        assertThat(copy.getName()).isEqualTo("Wing");
        assertThat(copy.getOutput()).isEqualTo(ExperienceOutput.DISPLAY);
        assertThat(copy.getStartCardTypeId()).isEqualTo(7L);
        assertThat(copy.getContent()).contains("\"sceneKey\":\"1\"");
        assertThat(copy.getMediaIds()).containsExactly(video.toString());
        assertThat(copy.getCardTypeIds()).containsExactly(7L);
        assertThat(copy.getPublishedAt()).isEqualTo(NOW);
        assertThat(status).isEqualTo(new PublishStatus(1, 5, NOW, false));
        verify(boxStartCards).requireFreeOnAssignedBoxes(projectId, 7L);
    }

    @Test
    void changedDraft_isPublishedAsTheNextVersion() {
        stubReadyDraft();
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId))
                .thenReturn(Optional.of(copyOf(project(4), 2)));

        PublishStatus status = useCase.execute(projectId);

        assertThat(savedCopy().getVersion()).isEqualTo(3);
        assertThat(status.version()).isEqualTo(3);
    }

    @Test
    void unchangedDraft_isANoOp_returningTheLatestVersion() {
        stubAccess();
        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId))
                .thenReturn(Optional.of(copyOf(project(5), 2)));

        PublishStatus status = useCase.execute(projectId);

        assertThat(status.version()).isEqualTo(2);
        assertThat(status.hasUnpublishedChanges()).isFalse();
        verify(publishedExperienceRepository, never()).save(any());
        verifyNoInteractions(experienceGaps, getExperienceQuery);
    }

    @Test
    void draftWithGaps_isRefusedWithTheGaps_andNothingIsSaved() {
        stubAccess();
        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.empty());
        when(experienceGaps.find(projectId)).thenReturn(List.of("block 'b1' has no file chosen"));

        assertThatThrownBy(() -> useCase.execute(projectId))
                .isInstanceOf(ExperienceNotReadyException.class)
                .extracting(e -> ((ExperienceNotReadyException) e).getGaps())
                .isEqualTo(List.of("block 'b1' has no file chosen"));

        verify(publishedExperienceRepository, never()).save(any());
    }

    @Test
    void startCardTakenOnAnAssignedBox_isRefused_andNothingIsSaved() {
        stubReadyDraft();
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.empty());
        doThrow(new StartCardInUseException("taken")).when(boxStartCards).requireFreeOnAssignedBoxes(projectId, 7L);

        assertThatThrownBy(() -> useCase.execute(projectId)).isInstanceOf(StartCardInUseException.class);

        verify(publishedExperienceRepository, never()).save(any());
    }

    @Test
    void notOrgManager_neverTouchesTheDraft() {
        stubAccess();
        when(orgAccessGuard.requireManager(orgId)).thenThrow(new OrgManagerRequiredException("not a manager"));

        assertThatThrownBy(() -> useCase.execute(projectId)).isInstanceOf(OrgManagerRequiredException.class);

        verifyNoInteractions(projectRepository, publishedExperienceRepository);
    }

    @Test
    void projectAccessDenied_neverChecksTheRole() {
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> useCase.execute(projectId)).isInstanceOf(CatalogueNotFoundException.class);

        verifyNoInteractions(orgAccessGuard, projectRepository, publishedExperienceRepository);
    }

    private void stubAccess() {
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(new CatalogueProjectDto(
                projectId.toString(), orgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), NOW, NOW));
    }

    // A draft at doc version 5 without gaps: one start scene, started by card 7, showing a video.
    private void stubReadyDraft() {
        stubAccess();
        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(experienceGaps.find(projectId)).thenReturn(List.of());

        ExperienceFlow flow = new ExperienceFlow();
        flow.setOutput(ExperienceOutput.DISPLAY);
        ScanObjectType card = new ScanObjectType();
        card.setId(7L);
        Scene start = new Scene();
        start.setSceneKey("1");
        start.setName("Start");
        start.setPosition(0);
        start.setStart(true);
        start.setInitCardType(card);
        Block block = new Block();
        block.setBlockKey("b1");
        block.setType(BlockType.VIDEO);
        block.setPosition(0);
        block.setContent("{\"mediaId\":\"" + video + "\"}");
        when(getExperienceQuery.execute(projectId)).thenReturn(new ExperienceView(projectId, 5, 2, 2, 1, flow, List.of(),
                List.of(new ExperienceView.SceneView(start, List.of(block), List.of()))));
    }

    private PublishedExperience savedCopy() {
        ArgumentCaptor<PublishedExperience> captor = ArgumentCaptor.forClass(PublishedExperience.class);
        verify(publishedExperienceRepository).save(captor.capture());
        return captor.getValue();
    }

    private Project project(int docVersion) {
        Project project = new Project();
        project.setId(projectId);
        project.setDocVersion(docVersion);
        return project;
    }

    private static PublishedExperience copyOf(Project draft, int version) {
        return new PublishedExperience(draft, version, "Wing", ExperienceOutput.DISPLAY, 7L, "{}", List.of(), List.of(7L),
                NOW.minusSeconds(3600), "someone");
    }
}
