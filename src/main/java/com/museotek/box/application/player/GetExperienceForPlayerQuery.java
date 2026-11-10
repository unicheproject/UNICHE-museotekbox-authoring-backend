package com.museotek.box.application.player;

import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.ExperienceNotPublishedException;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The latest published copy of an experience on a Box, with its media list. Only for experiences assigned to that Box
 * and not deleted, so the player project can't read another museum's content through a Box id it doesn't run.
 */
@Service
public class GetExperienceForPlayerQuery {

    private final BoxRepository boxRepository;
    private final ProjectRepository projectRepository;
    private final PublishedExperienceRepository publishedExperienceRepository;
    private final MediaRepository mediaRepository;

    public GetExperienceForPlayerQuery(
            BoxRepository boxRepository,
            ProjectRepository projectRepository,
            PublishedExperienceRepository publishedExperienceRepository,
            MediaRepository mediaRepository
    ) {
        this.boxRepository = boxRepository;
        this.projectRepository = projectRepository;
        this.publishedExperienceRepository = publishedExperienceRepository;
        this.mediaRepository = mediaRepository;
    }

    @Transactional(readOnly = true)
    public PlayerExperienceCopy execute(Long boxId, UUID projectId) {
        if (!boxRepository.existsById(boxId)) {
            throw new BoxNotFoundException("No box " + boxId);
        }
        if (!boxRepository.findAssignedProjectIds(boxId).contains(projectId)) {
            throw notOnBox(boxId, projectId);
        }
        Project project = projectRepository.findById(projectId).orElseThrow(() -> notOnBox(boxId, projectId));
        if (project.getDeletedAt() != null) {
            throw notOnBox(boxId, projectId);
        }
        PublishedExperience latest = publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)
                .orElseThrow(() -> notOnBox(boxId, projectId));

        List<UUID> mediaIds = latest.getMediaIds().stream().map(UUID::fromString).toList();
        List<Media> media = mediaRepository.findAllById(mediaIds);
        return new PlayerExperienceCopy(PlayerExperience.of(latest), latest.getPublishedAt(), latest.getContent(), media);
    }

    private static ExperienceNotPublishedException notOnBox(Long boxId, UUID projectId) {
        return new ExperienceNotPublishedException("Experience " + projectId + " is not published on box " + boxId);
    }
}
