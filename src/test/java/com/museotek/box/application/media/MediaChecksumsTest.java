package com.museotek.box.application.media;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MediaChecksumsTest {

    // SHA-256 of "abc", from FIPS 180-2.
    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void whileReading_givesTheHashOfEverythingRead() throws Exception {
        DigestInputStream in = MediaChecksums.sha256While(new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)));

        assertThat(in.readAllBytes()).hasSize(3);
        assertThat(MediaChecksums.hex(in)).isEqualTo(ABC);
    }

    @Test
    void ofAFile_readsItWhole() {
        assertThat(MediaChecksums.sha256Of(new ByteArrayResource("abc".getBytes(StandardCharsets.US_ASCII)))).isEqualTo(ABC);
    }
}
