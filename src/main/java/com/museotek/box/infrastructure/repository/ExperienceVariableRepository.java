package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.experience.ExperienceVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExperienceVariableRepository extends JpaRepository<ExperienceVariable, Long> {
    List<ExperienceVariable> findByProjectIdOrderByVariableKeyAsc(UUID projectId);
}
