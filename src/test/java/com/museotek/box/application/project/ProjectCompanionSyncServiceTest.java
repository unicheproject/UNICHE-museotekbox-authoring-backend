package com.museotek.box.application.project;

import com.museotek.box.domain.project.Project;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectCompanionSyncServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCompanionSyncService service = new ProjectCompanionSyncService(projectRepository);

    @Test
    void upsert_createsNewRowWhenNoneExists() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(id, orgId, "Ancient Egypt Wing");

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getOrgId()).isEqualTo(orgId);
        assertThat(saved.getName()).isEqualTo("Ancient Egypt Wing");
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void upsert_updatesExistingRowAndClearsDeletedAt() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Project existing = new Project();
        existing.setId(id);
        existing.setDeletedAt(java.time.Instant.now());
        when(projectRepository.findById(id)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(id, orgId, "Renamed Wing");

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Renamed Wing");
        assertThat(captor.getValue().getDeletedAt()).isNull();
    }

    @Test
    void softDelete_setsDeletedAtOnExistingRow() {
        UUID id = UUID.randomUUID();
        Project existing = new Project();
        existing.setId(id);
        when(projectRepository.findById(id)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(id);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void softDelete_isNoOpWhenRowDoesNotExistLocally() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        service.softDelete(id);

        verify(projectRepository, never()).save(any());
    }
}
