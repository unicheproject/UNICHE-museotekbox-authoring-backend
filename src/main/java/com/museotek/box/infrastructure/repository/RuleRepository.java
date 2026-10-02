package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.rule.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RuleRepository extends JpaRepository<Rule, Long> {
    List<Rule> findBySceneIdInOrderByPositionAsc(Collection<Long> sceneIds);

    boolean existsByScanObjectTypeId(Long scanObjectTypeId);

    // effects is a jsonb array; any of an effect's media fields may hold a media library id.
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM rules r, jsonb_array_elements(r.effects) e
                WHERE e ->> 'media' = :mediaId OR e ->> 'audio' = :mediaId
                   OR e ->> 'boxVideo' = :mediaId OR e ->> 'url' = :mediaId)
            """, nativeQuery = true)
    boolean existsByEffectMediaId(@Param("mediaId") String mediaId);
}
