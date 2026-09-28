package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.scene.Scene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, Long> {
    List<Scene> findByProjectIdOrderByPositionAsc(UUID projectId);

    boolean existsByInitCardTypeId(Long initCardTypeId);
}
