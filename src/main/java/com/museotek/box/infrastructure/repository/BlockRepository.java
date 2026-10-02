package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.block.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {
    List<Block> findBySceneIdInOrderByPositionAsc(Collection<Long> sceneIds);

    // content is jsonb: a media block keeps the media library item it shows in content.mediaId.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM blocks WHERE content ->> 'mediaId' = :mediaId)", nativeQuery = true)
    boolean existsByMediaId(@Param("mediaId") String mediaId);
}
