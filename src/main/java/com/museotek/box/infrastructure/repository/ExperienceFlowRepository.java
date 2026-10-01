package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.experience.ExperienceFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExperienceFlowRepository extends JpaRepository<ExperienceFlow, UUID> {
}
