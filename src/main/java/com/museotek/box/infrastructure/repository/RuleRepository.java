package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.rule.Rule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RuleRepository extends JpaRepository<Rule, Long> {
    List<Rule> findBySceneIdInOrderByPositionAsc(Collection<Long> sceneIds);

    boolean existsByScanObjectTypeId(Long scanObjectTypeId);
}
