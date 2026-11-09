package com.museotek.box.infrastructure.security;

import com.museotek.box.support.FakeIdentityProvider;
import com.museotek.box.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real {@link PlayerSecurityConfig} chain next to the curator one. A test-only {@code /player/v1/ping}
 * stands in for the player endpoints until they exist. The secrets are the two in application-test.properties.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PlayerSecurityIntegrationTest.PingController.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlayerSecurityIntegrationTest {

    private static final String SECRET_ONE = "test-player-secret-one-0123456789abcdef";
    private static final String SECRET_TWO = "test-player-secret-two-0123456789abcdef";
    private static final String PING = "/player/v1/ping";

    private static FakeIdentityProvider idp;

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class PingController {
        @GetMapping(PING)
        String ping() {
            return "pong";
        }
    }

    @BeforeAll
    static void startFakeIdp() {
        idp = FakeIdentityProvider.start();
    }

    @AfterAll
    static void stopFakeIdp() {
        idp.stop();
    }

    @DynamicPropertySource
    static void wireIssuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> idp.issuerUri());
    }

    private String validCuratorJwt() {
        Date now = new Date();
        return idp.signedJwt(UUID.randomUUID().toString(), "alice", idp.issuerUri(), List.of("uniche-platform"),
                now, new Date(now.getTime() + 60_000));
    }

    @Test
    void noSecret_returns401_withTheUsualErrorBody() throws Exception {
        mockMvc.perform(get(PING))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void aWrongSecret_returns401() throws Exception {
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer not-the-player-secret-0123456789abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theSecretWithoutTheBearerScheme_returns401() throws Exception {
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, SECRET_ONE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eitherSecret_isLetIn() throws Exception {
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET_ONE))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET_TWO))
                .andExpect(status().isOk());
    }

    @Test
    void aValidCuratorToken_isRefusedOnThePlayerPath() throws Exception {
        mockMvc.perform(get(PING).header(HttpHeaders.AUTHORIZATION, "Bearer " + validCuratorJwt()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void thePlayerSecret_isRefusedOnTheCuratorApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + SECRET_ONE))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aBrowserPreflight_onThePlayerPath_getsNoCorsPermission() throws Exception {
        mockMvc.perform(options(PING)
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
