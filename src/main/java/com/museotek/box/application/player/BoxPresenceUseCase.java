package com.museotek.box.application.player;

import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.domain.project.Project;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * A Box's online status, from its socket: online while connected, offline once it drops. What it is playing is
 * cleared either way; the Box's own state messages set it.
 * <p>
 * Each write runs in a transaction of its own ({@code REQUIRES_NEW}): it is also called from after-commit listeners
 * (a replaced key closes the Box), where the finished transaction is still bound and joining it would fail with
 * "no transaction is in progress".
 */
@Service
public class BoxPresenceUseCase {

    private final BoxRepository boxRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    public BoxPresenceUseCase(BoxRepository boxRepository, ProjectRepository projectRepository, Clock clock) {
        this.boxRepository = boxRepository;
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOnline(Long boxId) {
        boxRepository.setPresence(boxId, BoxStatus.ONLINE, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOffline(Long boxId) {
        boxRepository.setPresence(boxId, BoxStatus.OFFLINE, clock.instant());
    }

    /**
     * What the Box says it is playing ({@code null} when idle). Only an experience assigned to this Box and not
     * deleted is stored; anything else is stored as nothing playing, since the Box may have a stale copy.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reportPlaying(Long boxId, UUID projectId, String sceneKey) {
        Project project = liveOnBox(boxId, projectId).orElse(null);
        boxRepository.setPlaying(boxId, project, project == null ? null : sceneKey);
    }

    private Optional<Project> liveOnBox(Long boxId, UUID projectId) {
        if (projectId == null || !boxRepository.findAssignedProjectIds(boxId).contains(projectId)) {
            return Optional.empty();
        }
        return projectRepository.findById(projectId).filter(project -> project.getDeletedAt() == null);
    }

    /** At startup no Box is connected yet, whatever the table says from before a restart or crash. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markAllOffline() {
        return boxRepository.markAllOffline(BoxStatus.OFFLINE, clock.instant());
    }
}
