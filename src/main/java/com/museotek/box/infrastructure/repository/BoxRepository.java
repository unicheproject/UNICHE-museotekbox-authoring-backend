package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.box.Box;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoxRepository extends JpaRepository<Box, Long> {
    Optional<Box> findBySerialNumber(String serialNumber);

    List<Box> findByOrgId(UUID orgId);

    // Scoped by construction: a wrong-org guess against Box's low-cardinality Long id 404s here
    // rather than via a post-fetch comparison, since ids are sequential/guessable unlike a UUID.
    Optional<Box> findByIdAndOrgId(Long id, UUID orgId);

    // Ids only, so the lazy assignedProjects collection never has to be initialised
    // (open-in-view is off, and a read query has no transaction of its own).
    @Query("select p.id from Box b join b.assignedProjects p where b.id = :boxId")
    List<UUID> findAssignedProjectIds(@Param("boxId") Long boxId);

    // Names of the other experiences on this box whose start scene uses this card.
    @Query("""
            select p.name from Box b join b.assignedProjects p, Scene s
            where b.id = :boxId and p.id <> :projectId and p.deletedAt is null
              and s.project = p and s.start = true and s.initCardType.id = :cardTypeId
            """)
    List<String> findProjectNamesWithStartCard(
            @Param("boxId") Long boxId, @Param("projectId") UUID projectId, @Param("cardTypeId") Long cardTypeId);

    // Names of the boxes this experience is on where another experience's start scene uses this card.
    @Query("""
            select distinct b.name from Box b join b.assignedProjects mine join b.assignedProjects other, Scene s
            where mine.id = :projectId and other.id <> :projectId and other.deletedAt is null
              and s.project = other and s.start = true and s.initCardType.id = :cardTypeId
            """)
    List<String> findBoxNamesWhereStartCardIsTaken(@Param("projectId") UUID projectId, @Param("cardTypeId") Long cardTypeId);
}
