package com.museotek.box.application.screen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenSecretsTest {

    @Test
    void setupCode_isTwoGroupsOfFour_withoutLookAlikeCharacters() {
        for (int i = 0; i < 200; i++) {
            assertThat(ScreenSecrets.newSetupCode()).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
        }
    }

    @Test
    void normalise_ignoresCaseDashesAndSpaces() {
        assertThat(ScreenSecrets.normaliseSetupCode(" k7qf m2-xr ")).isEqualTo("K7QFM2XR");
        assertThat(ScreenSecrets.normaliseSetupCode(null)).isEmpty();
    }

    @Test
    void screenKey_is43UrlSafeCharacters_andNewEachTime() {
        String key = ScreenSecrets.newScreenKey();

        assertThat(key).matches("[A-Za-z0-9_-]{43}");
        assertThat(ScreenSecrets.newScreenKey()).isNotEqualTo(key);
    }

    @Test
    void sha256Hex_isTheKnownValue() {
        assertThat(ScreenSecrets.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
