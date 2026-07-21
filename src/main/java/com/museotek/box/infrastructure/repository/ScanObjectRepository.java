package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.scanobject.ScanObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScanObjectRepository extends JpaRepository<ScanObject, Long> {
    Optional<ScanObject> findByRfidTag(String rfidTag);
}
