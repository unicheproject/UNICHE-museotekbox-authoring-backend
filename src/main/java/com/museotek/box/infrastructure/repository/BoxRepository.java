package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.box.Box;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BoxRepository extends JpaRepository<Box, Long> {
    Optional<Box> findBySerialNumber(String serialNumber);
}
