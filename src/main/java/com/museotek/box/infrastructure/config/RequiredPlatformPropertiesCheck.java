package com.museotek.box.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;

/**
 * Refuses to start without the two platform coordinates that have no safe default: the Keycloak
 * issuer and the Catalogue base URL. Runs before any bean exists — including the datasource — so
 * a misconfigured deployment fails immediately with a clear message instead of either failing
 * later with an unrelated database error, or starting and silently talking to the wrong platform
 * instance.
 */
public class RequiredPlatformPropertiesCheck implements EnvironmentPostProcessor {

    private record RequiredProperty(String property, String environmentVariable, String describes) {
    }

    private static final List<RequiredProperty> REQUIRED = List.of(
            new RequiredProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                    "IDP_ISSUER_URI", "the Keycloak realm this backend trusts"),
            new RequiredProperty("uniche.catalogue.base-url",
                    "CATALOGUE_BASE_URL", "the UNICHE Catalogue instance that authorises access"));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (RequiredProperty required : REQUIRED) {
            if (isBlank(valueOf(environment, required.property()))) {
                throw new IllegalStateException(
                        "%s is not configured. Set the %s environment variable to %s. It has no default: a default here would let a misconfigured deployment start against the wrong platform instance."
                                .formatted(required.property(), required.environmentVariable(), required.describes()));
            }
        }
    }

    /** An unresolvable placeholder is the same situation as an absent property: the env var behind it was never set. */
    private static String valueOf(ConfigurableEnvironment environment, String property) {
        try {
            return environment.getProperty(property);
        } catch (RuntimeException unresolvable) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
