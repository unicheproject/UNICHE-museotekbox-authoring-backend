package com.museotek.box.application.secret;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretTokensTest {

    @Test
    void newToken_is43UrlSafeCharacters_andDifferentEachTime() {
        String token = SecretTokens.newToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(SecretTokens.newToken()).isNotEqualTo(token);
    }

    @Test
    void sha256Hex_isTheKnownDigest() {
        assertThat(SecretTokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
