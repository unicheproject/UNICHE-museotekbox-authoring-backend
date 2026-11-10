package com.museotek.box.application.player;

import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.ExperienceNotPublishedException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportBoxStatusUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant AT = Instant.parse("2026-11-10T11:59:58Z");

    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ReportBoxStatusUseCase useCase =
            new ReportBoxStatusUseCase(boxRepository, projectRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID projectId = UUID.randomUUID();

    @Test
    void aRunningExperienceOnTheBox_isStoredWithItsScene_andTheBoxIsSeenNow() {
        Project project = project();
        boxExists();
        when(boxRepository.findAssignedProjectIds(12L)).thenReturn(List.of(projectId));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        useCase.execute(12L, new BoxStatusReport(true, projectId, "track-3", AT));

        verify(boxRepository).touchLastSeen(12L, NOW);
        verify(boxRepository).applyStatusIfNewer(12L, BoxStatus.ONLINE, project, "track-3", AT);
    }

    @Test
    void anIdleBox_hasNoExperienceAndNoScene() {
        boxExists();

        useCase.execute(12L, new BoxStatusReport(true, null, "track-3", AT));

        verify(boxRepository).applyStatusIfNewer(12L, BoxStatus.ONLINE, null, null, AT);
    }

    @Test
    void anOfflineBox_runsNothing_whateverTheReportSays() {
        boxExists();

        useCase.execute(12L, new BoxStatusReport(false, projectId, "track-3", AT));

        verify(boxRepository).applyStatusIfNewer(12L, BoxStatus.OFFLINE, null, null, AT);
        verify(projectRepository, never()).findById(any());
    }

    @Test
    void anExperienceNotOnTheBox_isRejected_andNothingIsStored() {
        boxExists();
        when(boxRepository.findAssignedProjectIds(12L)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(12L, new BoxStatusReport(true, projectId, null, AT)))
                .isInstanceOf(ExperienceNotPublishedException.class);
        verify(boxRepository, never()).touchLastSeen(any(), any());
    }

    @Test
    void aDeletedExperience_isRejected() {
        Project project = project();
        project.setDeletedAt(NOW);
        boxExists();
        when(boxRepository.findAssignedProjectIds(12L)).thenReturn(List.of(projectId));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> useCase.execute(12L, new BoxStatusReport(true, projectId, null, AT)))
                .isInstanceOf(ExperienceNotPublishedException.class);
    }

    @Test
    void anUnknownBox_throws() {
        when(boxRepository.existsById(12L)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(12L, new BoxStatusReport(true, null, null, AT)))
                .isInstanceOf(BoxNotFoundException.class);
    }

    private void boxExists() {
        when(boxRepository.existsById(12L)).thenReturn(true);
    }

    private Project project() {
        Project project = new Project();
        project.setId(projectId);
        return project;
    }
}
