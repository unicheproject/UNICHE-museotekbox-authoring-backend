package com.museotek.box.infrastructure.security;

import com.museotek.box.application.player.DisplayMediaLinks;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real {@code /display/**} chain against the database: the display token logs in, nothing else does, it only
 * sees its own Box, CORS for the CMS, and media through signed links without a token.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisplaySecurityIntegrationTest {

    private static final String CMS_ORIGIN = "http://localhost:5173";

    private static FakeIdentityProvider idp;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BoxRepository boxRepository;
    @Autowired
    private DisplaySessionRepository sessionRepository;
    @Autowired
    private DisplayMediaLinks mediaLinks;

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

    private record Display(UUID sessionId, Box box, String token) {
    }

    private Display newDisplay(String boxName) {
        Box box = new Box();
        box.setOrgId(UUID.randomUUID());
        box.setName(boxName);
        box.setSerialNumber("SN-" + UUID.randomUUID());
        box = boxRepository.save(box);
        String token = SecretTokens.newToken();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        DisplaySession session = new DisplaySession();
        session.setId(UUID.randomUUID());
        session.setBoxId(box.getId());
        session.setTokenHash(SecretTokens.sha256Hex(token));
        session.setOrgName("Museum of Art");
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(1, ChronoUnit.DAYS));
        sessionRepository.save(session);
        return new Display(session.getId(), box, token);
    }

    private String validCuratorJwt() {
        Date now = new Date();
        return idp.signedJwt(UUID.randomUUID().toString(), "alice", idp.issuerUri(), List.of("uniche-platform"),
                now, new Date(now.getTime() + 60_000));
    }

    @Test
    void theDisplayToken_getsItsOwnBoxsIdleScreen() throws Exception {
        Display display = newDisplay("Hall A");
        newDisplay("Hall B");

        mockMvc.perform(get("/display/idle").header(HttpHeaders.AUTHORIZATION, "Bearer " + display.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgName").value("Museum of Art"))
                .andExpect(jsonPath("$.boxId").value(display.box().getId()))
                .andExpect(jsonPath("$.boxName").value("Hall A"))
                .andExpect(jsonPath("$.boxStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.experiences").isEmpty());
    }

    @Test
    void noOrAWrongToken_is401_withItsOwnCode() throws Exception {
        mockMvc.perform(get("/display/idle"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DISPLAY_TOKEN_INVALID"));
        mockMvc.perform(get("/display/idle").header(HttpHeaders.AUTHORIZATION, "Bearer " + SecretTokens.newToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DISPLAY_TOKEN_INVALID"));
    }

    @Test
    void aRevokedDisplay_is401() throws Exception {
        Display display = newDisplay("Hall A");
        DisplaySession session = sessionRepository.findById(display.sessionId()).orElseThrow();
        session.setRevokedAt(Instant.now());
        sessionRepository.save(session);

        mockMvc.perform(get("/display/idle").header(HttpHeaders.AUTHORIZATION, "Bearer " + display.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCuratorToken_isRefusedOnTheDisplayApi() throws Exception {
        mockMvc.perform(get("/display/idle").header(HttpHeaders.AUTHORIZATION, "Bearer " + validCuratorJwt()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theDisplayToken_isRefusedOnTheCuratorApi() throws Exception {
        Display display = newDisplay("Hall A");

        mockMvc.perform(get("/api/v1/me/authorization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + display.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theCmsOrigin_passesThePreflight() throws Exception {
        mockMvc.perform(options("/display/idle")
                        .header(HttpHeaders.ORIGIN, CMS_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CMS_ORIGIN));
    }

    @Test
    void media_needsNoToken_butAValidLink() throws Exception {
        Display display = newDisplay("Hall A");
        UUID mediaId = UUID.randomUUID();

        // Signed, but the media is on no experience of the Box: the link got through security to the query.
        mockMvc.perform(get(mediaLinks.link(display.sessionId(), mediaId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        mockMvc.perform(get("/display/media/" + mediaId + "/content")
                        .param("session", display.sessionId().toString())
                        .param("expires", String.valueOf(Instant.now().plusSeconds(600).getEpochSecond()))
                        .param("signature", "made-up"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEDIA_LINK_INVALID"));
    }

    @Test
    void otherDisplayPaths_needTheToken() throws Exception {
        mockMvc.perform(get("/display/media/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
