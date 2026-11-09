package com.museotek.box.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerSecretsTest {

    private static final String ONE = "player-secret-one-0123456789abcdefgh";
    private static final String TWO = "player-secret-two-0123456789abcdefgh";

    @Test
    void aMissingSecret_stopsTheStartup_andNamesTheVariable() {
        assertThatThrownBy(() -> new PlayerSecrets(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLAYER_SECRETS");
        assertThatThrownBy(() -> new PlayerSecrets(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aShortSecret_stopsTheStartup_withoutPrintingIt() {
        String shortSecret = "too-short-secret";

        assertThatThrownBy(() -> new PlayerSecrets(ONE + "," + shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32")
                .hasMessageNotContaining(shortSecret);
    }

    @Test
    void anEmptyEntry_countsAsTooShort() {
        assertThatThrownBy(() -> new PlayerSecrets(ONE + ","))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    void threeSecrets_stopTheStartup() {
        assertThatThrownBy(() -> new PlayerSecrets(ONE + "," + TWO + "," + ONE + "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at most 2");
    }

    @Test
    void eitherOfTwoSecrets_matches() {
        PlayerSecrets secrets = new PlayerSecrets(ONE + "," + TWO);

        assertThat(secrets.matches(ONE)).isTrue();
        assertThat(secrets.matches(TWO)).isTrue();
    }

    @Test
    void spacesAroundTheComma_areIgnored() {
        PlayerSecrets secrets = new PlayerSecrets(" " + ONE + " , " + TWO + " ");

        assertThat(secrets.matches(ONE)).isTrue();
        assertThat(secrets.matches(TWO)).isTrue();
    }

    @Test
    void aWrongOrPartialOrMissingCandidate_doesNotMatch() {
        PlayerSecrets secrets = new PlayerSecrets(ONE);

        assertThat(secrets.matches("something-else-entirely-0123456789")).isFalse();
        assertThat(secrets.matches(ONE.substring(0, ONE.length() - 1))).isFalse();
        assertThat(secrets.matches(ONE + "x")).isFalse();
        assertThat(secrets.matches("")).isFalse();
        assertThat(secrets.matches(null)).isFalse();
    }
}
