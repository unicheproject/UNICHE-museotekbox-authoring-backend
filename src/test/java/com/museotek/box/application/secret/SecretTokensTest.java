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
    void newBoxKey_is16CharactersWithoutLookalikes_inGroupsOfFour_andDifferentEachTime() {
        String key = SecretTokens.newBoxKey();

        assertThat(key).matches("[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}");
        assertThat(SecretTokens.newBoxKey()).isNotEqualTo(key);
    }

    @Test
    void boxKeyHash_ignoresDashesSpacesAndCase_andReadsLookalikesAsDigits() {
        String hash = SecretTokens.boxKeyHash("K7QM-3XRD-9F01-2HWB");

        assertThat(hash).isEqualTo(SecretTokens.sha256Hex("K7QM3XRD9F012HWB"));
        assertThat(SecretTokens.boxKeyHash("k7qm3xrd9f012hwb")).isEqualTo(hash);
        assertThat(SecretTokens.boxKeyHash(" K7QM 3XRD 9F01 2HWB ")).isEqualTo(hash);
        assertThat(SecretTokens.boxKeyHash("K7QM-3XRD-9FOl-2HWB")).isEqualTo(hash);
        assertThat(SecretTokens.boxKeyHash("K7QM-3XRD-9FOI-2HWB")).isEqualTo(hash);
        assertThat(SecretTokens.boxKeyHash("K7QM-3XRD-9F02-2HWB")).isNotEqualTo(hash);
    }

    @Test
    void sha256Hex_isTheKnownDigest() {
        assertThat(SecretTokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
