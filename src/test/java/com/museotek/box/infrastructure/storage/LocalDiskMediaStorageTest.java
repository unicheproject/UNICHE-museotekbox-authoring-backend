package com.museotek.box.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskMediaStorageTest {

    @TempDir
    Path root;

    private LocalDiskMediaStorage storage() {
        return new LocalDiskMediaStorage(new MediaProperties(root.toString(), Map.of()));
    }

    @Test
    void store_writesUnderOrgFolderWithGeneratedName_andLoadReadsItBack() throws Exception {
        UUID orgId = UUID.randomUUID();
        LocalDiskMediaStorage storage = storage();

        String storedFilename = storage.store(orgId, ".jpg", bytes("picture"));

        assertThat(storedFilename).startsWith(orgId + "/").endsWith(".jpg");
        assertThat(Files.readString(root.resolve(storedFilename))).isEqualTo("picture");
        Resource loaded = storage.load(storedFilename);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("picture");
    }

    @Test
    void store_givesEachFileItsOwnName() {
        UUID orgId = UUID.randomUUID();
        LocalDiskMediaStorage storage = storage();

        String first = storage.store(orgId, ".png", bytes("a"));
        String second = storage.store(orgId, ".png", bytes("b"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void store_refusesAnUnsafeExtension() {
        LocalDiskMediaStorage storage = storage();

        assertThatThrownBy(() -> storage.store(UUID.randomUUID(), "/../x", bytes("a")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void load_missingFile_returnsNull() {
        assertThat(storage().load(UUID.randomUUID() + "/nothing.jpg")).isNull();
    }

    @Test
    void load_pathOutsideRoot_isRefused() {
        LocalDiskMediaStorage storage = storage();

        assertThatThrownBy(() -> storage.load("../outside.txt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_removesFile_andMissingFileIsFine() {
        LocalDiskMediaStorage storage = storage();
        String storedFilename = storage.store(UUID.randomUUID(), ".mp3", bytes("sound"));

        storage.delete(storedFilename);
        storage.delete(storedFilename);

        assertThat(Files.exists(root.resolve(storedFilename))).isFalse();
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
