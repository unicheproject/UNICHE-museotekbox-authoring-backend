package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.publish.PublishedExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PublishedExperienceRepository extends JpaRepository<PublishedExperience, UUID> {
    // The copy Boxes play.
    Optional<PublishedExperience> findTopByProjectIdOrderByVersionDesc(UUID projectId);
}
