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
}
