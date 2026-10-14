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

    // Blocks that show a file but have none chosen yet (a gap, see ExperienceGaps).
    @Query(value = """
            SELECT b.block_key FROM blocks b
            WHERE b.scene_id IN (:sceneIds)
              AND b.type IN ('IMAGE', 'ILLUSTRATION', 'VIDEO', 'SCREEN', 'AUDIO', 'BOX_VIDEO')
              AND COALESCE(b.content ->> 'mediaId', '') = ''
            """, nativeQuery = true)
    List<String> findMediaBlockKeysWithoutMedia(@Param("sceneIds") Collection<Long> sceneIds);

    // Scenes that play a video: a VIDEO or BOX_VIDEO block, or a SCREEN block showing a video file.
    @Query(value = """
            SELECT DISTINCT b.scene_id FROM blocks b
            LEFT JOIN media m ON CAST(m.id AS TEXT) = b.content ->> 'mediaId'
            WHERE b.scene_id IN (:sceneIds)
              AND (b.type IN ('VIDEO', 'BOX_VIDEO') OR (b.type = 'SCREEN' AND m.kind = 'VIDEO'))
            """, nativeQuery = true)
    List<Long> findSceneIdsWithVideo(@Param("sceneIds") Collection<Long> sceneIds);
}
