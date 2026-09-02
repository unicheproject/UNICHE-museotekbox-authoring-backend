package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.scanobject.ScanObjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScanObjectTypeRepository extends JpaRepository<ScanObjectType, Long> {
    Optional<ScanObjectType> findByIdAndOrgId(Long id, UUID orgId);
}
