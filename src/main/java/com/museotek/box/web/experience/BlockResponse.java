package com.museotek.box.web.experience;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.museotek.box.domain.block.Block;

// content is embedded raw rather than as an escaped string: it is a jsonb column, so the
// database itself guarantees whatever is stored there is already valid JSON.
public record BlockResponse(String blockKey, String type, Integer position, @JsonRawValue String content) {

    public static BlockResponse from(Block block) {
        return new BlockResponse(block.getBlockKey(), block.getType().name(), block.getPosition(), block.getContent());
    }
}
