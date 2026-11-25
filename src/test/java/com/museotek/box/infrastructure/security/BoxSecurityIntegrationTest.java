package com.museotek.box.infrastructure.security;

import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.infrastructure.repository.BoxRepository;
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

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real {@code /box/**} chain against the database: the Box key logs in, nothing else does, and a Box only reads
 * its own experiences and media.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoxSecurityIntegrationTest {

    private static FakeIdentityProvider idp;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BoxRepository boxRepository;

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

    private record KeyedBox(Box box, String key) {
    }

    private KeyedBox newBox(String name) {
        String key = SecretTokens.newToken();
        Box box = new Box();
        box.setOrgId(UUID.randomUUID());
        box.setName(name);
        box.setSerialNumber("SN-" + UUID.randomUUID());
        box.setKeyHash(SecretTokens.sha256Hex(key));
        box.setKeyCreatedAt(Instant.now());
        return new KeyedBox(boxRepository.save(box), key);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void theBoxKey_syncsItsOwnBox() throws Exception {
        KeyedBox hallA = newBox("Hall A");
        newBox("Hall B");

        mockMvc.perform(get("/box/sync").header(HttpHeaders.AUTHORIZATION, bearer(hallA.key())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.box.id").value(hallA.box().getId()))
                .andExpect(jsonPath("$.box.name").value("Hall A"))
                .andExpect(jsonPath("$.box.serialNumber").doesNotExist())
                .andExpect(jsonPath("$.experiences").isEmpty());
    }

    @Test
    void noOrAWrongKey_is401_withItsOwnCode() throws Exception {
        mockMvc.perform(get("/box/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BOX_KEY_INVALID"));
        mockMvc.perform(get("/box/sync").header(HttpHeaders.AUTHORIZATION, bearer(SecretTokens.newToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BOX_KEY_INVALID"));
    }

    @Test
    void aReplacedKey_stopsWorking() throws Exception {
        KeyedBox box = newBox("Hall A");
        box.box().setKeyHash(SecretTokens.sha256Hex(SecretTokens.newToken()));
        boxRepository.save(box.box());

        mockMvc.perform(get("/box/sync").header(HttpHeaders.AUTHORIZATION, bearer(box.key())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theSerialNumber_isNoKey() throws Exception {
        KeyedBox box = newBox("Hall A");

        mockMvc.perform(get("/box/sync").header(HttpHeaders.AUTHORIZATION, bearer(box.box().getSerialNumber())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCuratorToken_isRefusedOnTheBoxApi() throws Exception {
        Date now = new Date();
        String jwt = idp.signedJwt(UUID.randomUUID().toString(), "alice", idp.issuerUri(), List.of("uniche-platform"),
                now, new Date(now.getTime() + 60_000));

        mockMvc.perform(get("/box/sync").header(HttpHeaders.AUTHORIZATION, bearer(jwt)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theBoxKey_isRefusedEverywhereElse() throws Exception {
        KeyedBox box = newBox("Hall A");

        mockMvc.perform(get("/display/idle").header(HttpHeaders.AUTHORIZATION, bearer(box.key())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/authorization").header(HttpHeaders.AUTHORIZATION, bearer(box.key())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mediaNotOnTheBox_is404() throws Exception {
        KeyedBox box = newBox("Hall A");

        mockMvc.perform(get("/box/media/{mediaId}/content", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(box.key())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }
}
