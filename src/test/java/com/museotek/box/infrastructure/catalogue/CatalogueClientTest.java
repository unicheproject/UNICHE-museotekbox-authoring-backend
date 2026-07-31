package com.museotek.box.infrastructure.catalogue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.infrastructure.security.CurrentPrincipal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link CatalogueClient} against a real (in-process, JDK {@code HttpServer})
 * HTTP backend rather than mocking the internal {@code RestClient} — this is the only
 * way to genuinely test the {@code .onStatus(...)} mappings and the {@code /me/authorization}
 * ETag cache, since {@link CatalogueClient} builds its own {@code RestClient} internally.
 */
class CatalogueClientTest {

    private HttpServer server;
    private String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServerAndClearContext() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    private CatalogueClient client() {
        return new CatalogueClient(baseUrl);
    }

    private void authenticateAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("aud", java.util.List.of("uniche-platform"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void respondJson(com.sun.net.httpserver.HttpExchange exchange, int status, Object body) throws java.io.IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Map<String, Object> projectBody(UUID id, UUID orgId, String name) {
        return Map.of(
                "id", id.toString(),
                "orgId", orgId.toString(),
                "name", name,
                "slug", "ancient-egypt",
                "status", "ACTIVE",
                "tool", Map.of("slug", "museotek-box")
        );
    }

    @Test
    void getProject_success_returnsDeserialisedDto() throws Exception {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        server.createContext("/api/v1/projects/" + id, exchange ->
                respondJson(exchange, 200, projectBody(id, orgId, "Ancient Egypt Wing")));
        server.start();
        authenticateAs("user-1");

        CatalogueProjectDto result = client().getProject(id);

        assertThat(result.id()).isEqualTo(id.toString());
        assertThat(result.orgId()).isEqualTo(orgId.toString());
        assertThat(result.name()).isEqualTo("Ancient Egypt Wing");
        assertThat(result.tool().slug()).isEqualTo("museotek-box");
    }

    @Test
    void getProject_404_mapsToCatalogueNotFoundException() throws Exception {
        UUID id = UUID.randomUUID();
        server.createContext("/api/v1/projects/" + id, exchange -> respondJson(exchange, 404, Map.of("error", "not found")));
        server.start();
        authenticateAs("user-1");

        assertThatThrownBy(() -> client().getProject(id))
                .isInstanceOf(CatalogueNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void getProject_503_mapsToCatalogueUnavailableException() throws Exception {
        UUID id = UUID.randomUUID();
        server.createContext("/api/v1/projects/" + id, exchange -> respondJson(exchange, 503, Map.of("error", "unavailable")));
        server.start();
        authenticateAs("user-1");

        assertThatThrownBy(() -> client().getProject(id))
                .isInstanceOf(CatalogueUnavailableException.class);
    }

    @Test
    void createProject_409_mapsToCatalogueConflictException() throws Exception {
        UUID orgId = UUID.randomUUID();
        server.createContext("/api/v1/organisations/" + orgId + "/projects", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respondJson(exchange, 409, Map.of("error", "conflict"));
            }
        });
        server.start();
        authenticateAs("user-1");

        CatalogueCreateProjectRequest request = new CatalogueCreateProjectRequest("Wing", "wing", "museotek-box");
        assertThatThrownBy(() -> client().createProject(orgId, request))
                .isInstanceOf(CatalogueConflictException.class);
    }

    @Test
    void createProject_toolNotFound_mapsToCatalogueNotFoundException() throws Exception {
        // Regression test for the toolSlug mismatch bug: createProject previously only mapped 403,
        // so a 404 (unrecognised toolSlug) leaked as a raw HttpClientErrorException instead of a
        // clean domain exception. The status handler is now registered once for every call.
        UUID orgId = UUID.randomUUID();
        server.createContext("/api/v1/organisations/" + orgId + "/projects", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respondJson(exchange, 404, Map.of("code", "NOT_FOUND", "message", "Authoring tool not found"));
            }
        });
        server.start();
        authenticateAs("user-1");

        CatalogueCreateProjectRequest request = new CatalogueCreateProjectRequest("Wing", "wing", "not-a-real-tool");
        assertThatThrownBy(() -> client().createProject(orgId, request))
                .isInstanceOf(CatalogueNotFoundException.class);
    }

    @Test
    void getProject_408_mapsToCatalogueTimeoutException() throws Exception {
        UUID id = UUID.randomUUID();
        server.createContext("/api/v1/projects/" + id, exchange -> respondJson(exchange, 408, Map.of("error", "timeout")));
        server.start();
        authenticateAs("user-1");

        assertThatThrownBy(() -> client().getProject(id))
                .isInstanceOf(CatalogueTimeoutException.class);
    }

    @Test
    void getProject_unexpected4xx_mapsToCatalogueBadResponseException() throws Exception {
        UUID id = UUID.randomUUID();
        server.createContext("/api/v1/projects/" + id, exchange -> respondJson(exchange, 418, Map.of("error", "teapot")));
        server.start();
        authenticateAs("user-1");

        assertThatThrownBy(() -> client().getProject(id))
                .isInstanceOf(CatalogueBadResponseException.class);
    }

    @Test
    void getProject_connectionRefused_mapsToCatalogueUnavailableException() {
        // No server started at all — the RestClient never receives a status code, so this
        // exercises the transport-failure path (ResourceAccessException) rather than mapError().
        authenticateAs("user-1");
        CatalogueClient client = new CatalogueClient("http://localhost:1");

        assertThatThrownBy(() -> client.getProject(UUID.randomUUID()))
                .isInstanceOf(CatalogueUnavailableException.class);
    }

    @Test
    void createProject_403_mapsToCatalogueForbiddenException() throws Exception {
        UUID orgId = UUID.randomUUID();
        server.createContext("/api/v1/organisations/" + orgId + "/projects", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respondJson(exchange, 403, Map.of("error", "forbidden"));
            }
        });
        server.start();
        authenticateAs("user-1");

        CatalogueCreateProjectRequest request = new CatalogueCreateProjectRequest("Wing", "wing", "museotek-box");
        assertThatThrownBy(() -> client().createProject(orgId, request))
                .isInstanceOf(CatalogueForbiddenException.class)
                .hasMessageContaining(orgId.toString());
    }

    @Test
    void deleteProject_404_mapsToCatalogueNotFoundException() throws Exception {
        UUID id = UUID.randomUUID();
        server.createContext("/api/v1/projects/" + id, exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        authenticateAs("user-1");

        assertThatThrownBy(() -> client().deleteProject(id))
                .isInstanceOf(CatalogueNotFoundException.class);
    }

    @Test
    void getOrganisation_404_mapsToCatalogueNotFoundException() throws Exception {
        UUID orgId = UUID.randomUUID();
        server.createContext("/api/v1/organisations/" + orgId, exchange -> respondJson(exchange, 404, Map.of("error", "not found")));
        server.start();
        authenticateAs("user-1");

        assertThatThrownBy(() -> client().getOrganisation(orgId))
                .isInstanceOf(CatalogueNotFoundException.class);
    }

    @Test
    void getMeAuthorization_secondCallWithinTtl_isServedFromCacheWithoutANetworkRoundTrip() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/api/v1/me/authorization", exchange -> {
            hits.incrementAndGet();
            respondJson(exchange, 200, Map.of(
                    "subject", "user-1",
                    "platformAdmin", false,
                    "managedOrganisations", java.util.List.of(),
                    "projectMemberships", java.util.List.of(),
                    "etag", "etag-1",
                    "ttlSeconds", 60
            ));
        });
        server.start();
        authenticateAs("user-1");
        CatalogueClient client = client();

        CatalogueMeAuthorizationDto first = client.getMeAuthorization();
        CatalogueMeAuthorizationDto second = client.getMeAuthorization();

        assertThat(hits.get()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void getMeAuthorization_afterTtlExpiry_sendsIfNoneMatchAndKeepsCachedDtoOn304() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        AtomicInteger secondRequestIfNoneMatchHeaderCount = new AtomicInteger();
        server.createContext("/api/v1/me/authorization", exchange -> {
            int hitNumber = hits.incrementAndGet();
            if (hitNumber == 1) {
                respondJson(exchange, 200, Map.of(
                        "subject", "user-1",
                        "platformAdmin", false,
                        "managedOrganisations", java.util.List.of(),
                        "projectMemberships", java.util.List.of(),
                        "etag", "etag-1",
                        "ttlSeconds", 0
                ));
            } else {
                if ("etag-1".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                    secondRequestIfNoneMatchHeaderCount.incrementAndGet();
                }
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
            }
        });
        server.start();
        authenticateAs("user-1");
        CatalogueClient client = client();

        CatalogueMeAuthorizationDto first = client.getMeAuthorization();
        Thread.sleep(10); // ensure Instant.now() has moved past the (already-expired) ttl
        CatalogueMeAuthorizationDto second = client.getMeAuthorization();

        assertThat(hits.get()).isEqualTo(2);
        assertThat(secondRequestIfNoneMatchHeaderCount.get()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void getMeAuthorization_withNoJwtInContext_throwsIllegalStateException() {
        assertThatThrownBy(() -> client().getMeAuthorization())
                .isInstanceOf(IllegalStateException.class);
        assertThat(CurrentPrincipal.jwt()).isEmpty();
    }
}
