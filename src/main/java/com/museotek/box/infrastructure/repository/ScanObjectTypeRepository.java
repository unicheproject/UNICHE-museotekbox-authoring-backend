package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.scanobject.ScanObjectType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanObjectTypeRepository extends JpaRepository<ScanObjectType, Long> {
}
