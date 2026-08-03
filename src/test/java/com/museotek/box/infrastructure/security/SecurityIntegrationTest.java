package com.museotek.box.infrastructure.security;

import com.museotek.box.infrastructure.repository.UserRepository;
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

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real {@link SecurityConfig} filter chain end-to-end against
 * {@link FakeIdentityProvider}, a tiny in-process fake Keycloak realm, so
 * {@code JwtDecoders.fromIssuerLocation} does a real HTTP round trip and real RS256
 * signature verification, exactly like it would against Keycloak. A real, containerized
 * PostgreSQL (see {@link TestcontainersConfiguration}) backs JIT user provisioning, so the
 * concurrency test below exercises the actual unique-constraint/transaction behavior
 * production runs against, not an in-memory approximation of it.
 *
 * <p>Catalogue itself is intentionally left unreachable ({@code uniche.catalogue.base-url}
 * in application-test.properties points at a closed port) — these tests only assert on
 * the security/JIT layer, which runs before any controller talks to Catalogue. A 401/403
 * proves the security layer rejected the request; anything else (200 or an eventual 500
 * from the unreachable Catalogue call) proves it let the request through.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String REQUIRED_AUDIENCE = "uniche-platform";

    private static FakeIdentityProvider idp;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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

    private String validUserJwt(String subject) {
        Date now = new Date();
        return idp.signedJwt(subject, "user-" + subject, idp.issuerUri(), List.of(REQUIRED_AUDIENCE),
                now, new Date(now.getTime() + 60_000));
    }

    @Test
    void protectedEndpoint_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/authorization"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer this-is-not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withWrongIssuer_returns401() throws Exception {
        Date now = new Date();
        String token = idp.signedJwt(UUID.randomUUID().toString(), "alice", "https://evil.example.com/realms/other",
                List.of(REQUIRED_AUDIENCE), now, new Date(now.getTime() + 60_000));

        mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withMissingAudience_returns401() throws Exception {
        Date now = new Date();
        String token = idp.signedJwt(UUID.randomUUID().toString(), "alice", idp.issuerUri(),
                List.of(), now, new Date(now.getTime() + 60_000));

        mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withWrongAudience_returns401() throws Exception {
        Date now = new Date();
        String token = idp.signedJwt(UUID.randomUUID().toString(), "alice", idp.issuerUri(),
                List.of("some-other-platform"), now, new Date(now.getTime() + 60_000));

        mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        Date now = new Date();
        String token = idp.signedJwt(UUID.randomUUID().toString(), "alice", idp.issuerUri(),
                List.of(REQUIRED_AUDIENCE), new Date(now.getTime() - 120_000), new Date(now.getTime() - 60_000));

        mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpoint_isReachableWithoutAnyToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void validUserToken_passesAuthenticationAndTriggersJitProvisioning() throws Exception {
        String subject = "user-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validUserJwt(subject)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
        assertThat(userRepository.findBySubject(subject)).isPresent();
    }

    @Test
    void validServiceAccountToken_passesAuthenticationButSkipsJitProvisioning() throws Exception {
        String subject = "service-account-museotek-box-svc-" + UUID.randomUUID();
        Date now = new Date();
        String token = idp.signedJwt(subject, "service-account-museotek-box-svc", idp.issuerUri(),
                List.of(REQUIRED_AUDIENCE), now, new Date(now.getTime() + 60_000));

        MvcResult result = mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
        assertThat(userRepository.findBySubject(subject)).isEmpty();
    }

    @Test
    void concurrentFirstRequestsForSameNewSubject_neverLeakAnUnhandledFailureAndProvisionExactlyOneRow()
            throws Exception {
        String subject = "user-" + UUID.randomUUID();
        String token = validUserJwt(subject);
        int concurrency = 8;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CyclicBarrier barrier = new CyclicBarrier(concurrency);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();

        List<Runnable> tasks = java.util.stream.IntStream.range(0, concurrency)
                .<Runnable>mapToObj(i -> () -> {
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        mockMvc.perform(get("/api/v1/me/authorization")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
                    } catch (Throwable t) {
                        firstFailure.compareAndSet(null, t);
                    }
                })
                .toList();
        tasks.forEach(pool::execute);
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(firstFailure.get()).as("no request should throw an unhandled exception").isNull();
        assertThat(userRepository.findBySubject(subject)).isPresent();
    }
}
