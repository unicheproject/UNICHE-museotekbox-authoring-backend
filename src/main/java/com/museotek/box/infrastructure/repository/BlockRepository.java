package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.block.Block;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BlockRepository extends JpaRepository<Block, Long> {
    List<Block> findBySceneIdInOrderByPositionAsc(Collection<Long> sceneIds);
}
