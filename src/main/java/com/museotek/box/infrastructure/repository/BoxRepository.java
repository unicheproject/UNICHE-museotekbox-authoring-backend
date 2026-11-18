package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.domain.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoxRepository extends JpaRepository<Box, Long> {
    Optional<Box> findBySerialNumber(String serialNumber);

    Optional<Box> findByKeyHash(String keyHash);

    List<Box> findByOrgId(UUID orgId);

    // Scoped by construction: a wrong-org guess against Box's low-cardinality Long id 404s here
    // rather than via a post-fetch comparison, since ids are sequential/guessable unlike a UUID.
    Optional<Box> findByIdAndOrgId(Long id, UUID orgId);

    // Ids only, so the lazy assignedProjects collection never has to be initialised
    // (open-in-view is off, and a read query has no transaction of its own).
    @Query("select p.id from Box b join b.assignedProjects p where b.id = :boxId")
    List<UUID> findAssignedProjectIds(@Param("boxId") Long boxId);

    @Query("select b.id from Box b join b.assignedProjects p where p.id = :projectId")
    List<Long> findIdsByAssignedProject(@Param("projectId") UUID projectId);

    // From the Box's socket: connected or not. What it plays is cleared; its state messages set it again.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Box b set b.status = :status, b.currentProject = null, b.currentSceneKey = null,
                b.statusAt = :at, b.lastSeenAt = :at
            where b.id = :boxId
            """)
    int setPresence(@Param("boxId") Long boxId, @Param("status") BoxStatus status, @Param("at") Instant at);

    // From the Box's state messages: what it is playing now (null and null when idle).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Box b set b.currentProject = :project, b.currentSceneKey = :sceneKey where b.id = :boxId")
    int setPlaying(@Param("boxId") Long boxId, @Param("project") Project project, @Param("sceneKey") String sceneKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Box b set b.status = :offline, b.currentProject = null, b.currentSceneKey = null, b.statusAt = :at
            where b.status <> :offline
            """)
    int markAllOffline(@Param("offline") BoxStatus offline, @Param("at") Instant at);
}
