package com.museotek.box.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator();

    private Jwt jwtWithAudience(List<String> audience) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (audience != null) {
            builder.claim("aud", audience);
        } else {
            builder.claim("placeholder", "value");
        }
        return builder.build();
    }

    @Test
    void succeedsWhenRequiredAudiencePresent() {
        Jwt jwt = jwtWithAudience(List.of("uniche-platform"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void succeedsWhenRequiredAudienceIsOneOfSeveral() {
        Jwt jwt = jwtWithAudience(List.of("some-other-tool", "uniche-platform"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void failsWhenAudienceMissingEntirely() {
        Jwt jwt = jwtWithAudience(null);

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anySatisfy(error ->
                assertThat(error.getDescription()).contains("uniche-platform"));
    }

    @Test
    void failsWhenAudienceDoesNotContainRequiredValue() {
        Jwt jwt = jwtWithAudience(List.of("some-other-tool"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }
}
