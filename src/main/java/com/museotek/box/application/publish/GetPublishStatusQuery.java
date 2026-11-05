package com.museotek.box.application.publish;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.ExperienceNotPublishedException;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Which version of an experience Boxes play, and whether the draft has changed since. Anyone with access to the project. */
@Service
public class GetPublishStatusQuery {

    private final ProjectAccessGuard projectAccessGuard;
    private final ProjectRepository projectRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;

    public GetPublishStatusQuery(
            ProjectAccessGuard projectAccessGuard,
            ProjectRepository projectRepository,
            PublishedExperienceRepository publishedExperienceRepository
    ) {
        this.projectAccessGuard = projectAccessGuard;
        this.projectRepository = projectRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
    }

    public PublishStatus execute(UUID projectId) {
        projectAccessGuard.requireAccess(projectId);

        PublishedExperience latest = publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)
                .orElseThrow(() -> new ExperienceNotPublishedException("Experience " + projectId + " has never been published"));
        Project project = projectRepository.findById(projectId).orElseThrow();
        return PublishStatus.of(latest, project);
    }
}
