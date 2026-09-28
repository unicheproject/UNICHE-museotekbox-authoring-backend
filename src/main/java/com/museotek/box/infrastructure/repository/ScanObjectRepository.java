package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.scanobject.ScanObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanObjectRepository extends JpaRepository<ScanObject, Long> {
    Optional<ScanObject> findByRfidTag(String rfidTag);

    List<ScanObject> findByOrgId(UUID orgId);

    Optional<ScanObject> findByIdAndOrgId(Long id, UUID orgId);

    boolean existsByScanObjectTypeId(Long scanObjectTypeId);
}
