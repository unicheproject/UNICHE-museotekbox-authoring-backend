package com.museotek.box.application.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MediaFileSignaturesTest {

    @Test
    void recognisesEachAllowedType() {
        assertThat(MediaFileSignatures.matches("image/png", bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0))).isTrue();
        assertThat(MediaFileSignatures.matches("image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0))).isTrue();
        assertThat(MediaFileSignatures.matches("image/webp", ascii("RIFF\0\0\0\0WEBP"))).isTrue();
        assertThat(MediaFileSignatures.matches("audio/wav", ascii("RIFF\0\0\0\0WAVE"))).isTrue();
        assertThat(MediaFileSignatures.matches("audio/ogg", ascii("OggS\0\0"))).isTrue();
        assertThat(MediaFileSignatures.matches("audio/mpeg", ascii("ID3\u0004"))).isTrue();
        assertThat(MediaFileSignatures.matches("audio/mpeg", bytes(0xFF, 0xFB, 0x90, 0x00))).isTrue();
        assertThat(MediaFileSignatures.matches("video/mp4", ascii("\0\0\0\u0018ftypmp42"))).isTrue();
    }

    @Test
    void rejectsContentThatIsNotTheClaimedType() {
        byte[] text = ascii("<script>alert(1)");
        assertThat(MediaFileSignatures.matches("image/png", text)).isFalse();
        assertThat(MediaFileSignatures.matches("image/jpeg", text)).isFalse();
        assertThat(MediaFileSignatures.matches("video/mp4", text)).isFalse();
        // a WAV file claiming to be WebP: same RIFF container, different format
        assertThat(MediaFileSignatures.matches("image/webp", ascii("RIFF\0\0\0\0WAVE"))).isFalse();
    }

    @Test
    void rejectsTooShortHeadersAndUnknownTypes() {
        assertThat(MediaFileSignatures.matches("image/png", bytes(0x89, 'P'))).isFalse();
        assertThat(MediaFileSignatures.matches("image/svg+xml", ascii("<svg"))).isFalse();
        assertThat(MediaFileSignatures.matches(null, bytes(0xFF))).isFalse();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
