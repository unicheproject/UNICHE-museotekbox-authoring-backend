package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.screen.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScreenRepository extends JpaRepository<Screen, UUID> {

    // Removed screens are left out.
    List<Screen> findByOrgIdAndRevokedAtIsNullOrderByNameAsc(UUID orgId);

    Optional<Screen> findByIdAndOrgIdAndRevokedAtIsNull(UUID id, UUID orgId);

    Optional<Screen> findByKeyHashAndRevokedAtIsNull(String keyHash);
}
