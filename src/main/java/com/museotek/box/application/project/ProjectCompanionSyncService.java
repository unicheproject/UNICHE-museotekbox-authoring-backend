package com.museotek.box.application.project;

import com.museotek.box.domain.project.Project;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Implements the platform's "create-up" / "lazy-JIT" companion-row pattern for projects:
 * the local row is keyed by the Catalogue's own project UUID and materialised on demand,
 * never via a separate registration step.
 */
@Service
public class ProjectCompanionSyncService {

    private final ProjectRepository projectRepository;

    public ProjectCompanionSyncService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void upsert(UUID id, UUID orgId, String name) {
        Project project = projectRepository.findById(id).orElseGet(Project::new);
        project.setId(id);
        project.setOrgId(orgId);
        project.setName(name);
        project.setDeletedAt(null);
        projectRepository.save(project);
    }

    @Transactional
    public void softDelete(UUID id) {
        projectRepository.findById(id).ifPresent(project -> {
            project.setDeletedAt(Instant.now());
            projectRepository.save(project);
        });
    }
}
