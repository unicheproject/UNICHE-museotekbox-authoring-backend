package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.box.Box;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoxRepository extends JpaRepository<Box, Long> {
    Optional<Box> findBySerialNumber(String serialNumber);

    List<Box> findByOrgId(UUID orgId);

    // Scoped by construction: a wrong-org guess against Box's low-cardinality Long id 404s here
    // rather than via a post-fetch comparison, since ids are sequential/guessable unlike a UUID.
    Optional<Box> findByIdAndOrgId(Long id, UUID orgId);
}
