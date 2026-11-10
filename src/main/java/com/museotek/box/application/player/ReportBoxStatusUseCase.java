package com.museotek.box.application.player;

import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.ExperienceNotPublishedException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Stores what the player project reports about a Box. Always marks the Box as seen now. The status itself applies only
 * when the report is newer than the stored one, since reports can arrive out of order. An offline Box runs nothing we
 * know of, so its running experience and scene are cleared, whatever the report says.
 */
@Service
public class ReportBoxStatusUseCase {

    private final BoxRepository boxRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    public ReportBoxStatusUseCase(BoxRepository boxRepository, ProjectRepository projectRepository, Clock clock) {
        this.boxRepository = boxRepository;
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(Long boxId, BoxStatusReport report) {
        if (!boxRepository.existsById(boxId)) {
            throw new BoxNotFoundException("No box " + boxId);
        }
        Project running = null;
        String sceneKey = null;
        if (report.online() && report.runningProjectId() != null) {
            running = liveExperienceOnBox(boxId, report.runningProjectId());
            sceneKey = report.sceneKey();
        }
        BoxStatus status = report.online() ? BoxStatus.ONLINE : BoxStatus.OFFLINE;

        boxRepository.touchLastSeen(boxId, clock.instant());
        boxRepository.applyStatusIfNewer(boxId, status, running, sceneKey, report.at());
    }

    // Same rule as the player's reads: only an experience assigned to this Box and not deleted.
    private Project liveExperienceOnBox(Long boxId, UUID projectId) {
        if (!boxRepository.findAssignedProjectIds(boxId).contains(projectId)) {
            throw notOnBox(boxId, projectId);
        }
        Project project = projectRepository.findById(projectId).orElseThrow(() -> notOnBox(boxId, projectId));
        if (project.getDeletedAt() != null) {
            throw notOnBox(boxId, projectId);
        }
        return project;
    }

    private static ExperienceNotPublishedException notOnBox(Long boxId, UUID projectId) {
        return new ExperienceNotPublishedException("Experience " + projectId + " is not published on box " + boxId);
    }
}
