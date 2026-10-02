package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    List<Media> findByOrgIdOrderByUploadedAtDesc(UUID orgId);

    List<Media> findByOrgIdAndKindOrderByUploadedAtDesc(UUID orgId, MediaKind kind);

    // Scoped by construction, like BoxRepository.findByIdAndOrgId: another org's id is simply not found.
    Optional<Media> findByIdAndOrgId(UUID id, UUID orgId);

    List<Media> findByOrgIdAndIdIn(UUID orgId, Collection<UUID> ids);
}
