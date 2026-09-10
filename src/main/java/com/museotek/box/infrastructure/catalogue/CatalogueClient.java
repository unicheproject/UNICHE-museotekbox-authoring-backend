package com.museotek.box.infrastructure.catalogue;

import com.museotek.box.infrastructure.security.CurrentPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class CatalogueClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogueClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;

    // Per spec (GET /me/authorization is "cacheable, short-TTL"): keyed by subject, refreshed via
    // If-None-Match once the TTL Catalogue handed back has elapsed. Not a distributed cache — fine
    // for a single instance, and worst case on restart/scale-out is one extra Catalogue round trip.
    private final Map<String, CachedAuthorization> authorizationCache = new ConcurrentHashMap<>();

    private record CachedAuthorization(CatalogueMeAuthorizationDto dto, Instant expiresAt) {}

    public CatalogueClient(@Value("${uniche.catalogue.base-url}") String baseUrl) {
        // Default JDK HttpClient has no timeout at all if none is set — a hung/slow
        // Catalogue would otherwise block the request thread indefinitely instead of
        // failing fast.
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // Registered once here rather than per call: mapping per method is what let
                // createProject's 404 (an unrecognised toolSlug) go unmapped and leak a raw
                // HttpClientErrorException into the catch-all 500 handler.
                .defaultStatusHandler(HttpStatusCode::isError, CatalogueClient::mapError)
                .build();
    }

    public CatalogueProjectDto getProject(UUID projectId) {
        return call(() -> restClient.get()
                .uri("/api/v1/projects/{id}", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(CatalogueProjectDto.class));
    }

    public CatalogueProjectDto createProject(UUID orgId, CatalogueCreateProjectRequest request) {
        return call(() -> restClient.post()
                .uri("/api/v1/organisations/{orgId}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(request)
                .retrieve()
                .body(CatalogueProjectDto.class));
    }

    public void deleteProject(UUID projectId) {
        call(() -> restClient.delete()
                .uri("/api/v1/projects/{id}", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .toBodilessEntity());
    }

    public CatalogueMeAuthorizationDto getMeAuthorization() {
        String subject = CurrentPrincipal.subject()
                .orElseThrow(() -> new IllegalStateException("No JWT in security context"));
        Instant now = Instant.now();

        CachedAuthorization cached = authorizationCache.get(subject);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.dto();
        }

        ResponseEntity<CatalogueMeAuthorizationDto> response = call(() -> {
            var request = restClient.get()
                    .uri("/api/v1/me/authorization")
                    .header(HttpHeaders.AUTHORIZATION, bearer());
            if (cached != null) {
                request = request.header(HttpHeaders.IF_NONE_MATCH, cached.dto().etag());
            }
            return request.retrieve().toEntity(CatalogueMeAuthorizationDto.class);
        });

        // A 304 means our cached context is still current — Catalogue sends no body for it, so
        // keep the DTO we already have and just extend its expiry.
        CatalogueMeAuthorizationDto dto = response.getStatusCode() == HttpStatus.NOT_MODIFIED
                ? cached.dto()
                : response.getBody();

        authorizationCache.put(subject, new CachedAuthorization(dto, now.plusSeconds(dto.ttlSeconds())));
        return dto;
    }

    public List<CatalogueProjectDto> listProjectsForOrg(UUID orgId) {
        return call(() -> restClient.get()
                .uri("/api/v1/organisations/{orgId}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<List<CatalogueProjectDto>>() {}));
    }

    public CatalogueOrganisationDto getOrganisation(UUID orgId) {
        return call(() -> restClient.get()
                .uri("/api/v1/organisations/{orgId}", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(CatalogueOrganisationDto.class));
    }

    /** Invitations for a project, including accepted ones — the only way to see who currently
     * holds a Curator membership without a dedicated Catalogue endpoint for it. Manager-of-org
     * or admin only, per Catalogue's own rule on this endpoint. */
    public List<CatalogueInvitationSummaryDto> listInvitationsForProject(UUID projectId) {
        return call(() -> restClient.get()
                .uri("/api/v1/projects/{id}/invitations", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<List<CatalogueInvitationSummaryDto>>() {}));
    }

    public List<CatalogueManagerDto> listOrgManagers(UUID orgId) {
        return call(() -> restClient.get()
                .uri("/api/v1/organisations/{orgId}/managers", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<List<CatalogueManagerDto>>() {}));
    }

    // Catalogue's real endpoint is PUT, not PATCH — keep this call as PUT even though our own
    // /api/v1/projects/{id} controller endpoint is exposed as PATCH. Do not "fix" this to PATCH.
    public CatalogueProjectDto updateProject(UUID projectId, CatalogueUpdateProjectRequest request) {
        return call(() -> restClient.put()
                .uri("/api/v1/projects/{id}", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(request)
                .retrieve()
                .body(CatalogueProjectDto.class));
    }

    public List<CatalogueProjectDto> listDeletedProjectsForOrg(UUID orgId) {
        return call(() -> restClient.get()
                .uri("/api/v1/organisations/{orgId}/projects/deleted", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<List<CatalogueProjectDto>>() {}));
    }

    public CatalogueProjectDto restoreProject(UUID projectId) {
        return call(() -> restClient.post()
                .uri("/api/v1/projects/{id}/restore", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(CatalogueProjectDto.class));
    }

    /**
     * Translates transport failures (connection refused, read/connect timeout), which no status
     * handler can see because no status was ever received.
     */
    private <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ResourceAccessException transportFailure) {
            throw transportFailureOf(transportFailure);
        }
    }

    private static RuntimeException transportFailureOf(ResourceAccessException failure) {
        // Kept distinguishable all the way out to 504 vs 503: a timeout and an outage are
        // different failure modes worth different monitoring signals, even though no retry is
        // implemented on either path yet — this split only enables a future retry-on-timeout,
        // it doesn't perform one.
        for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                return new CatalogueTimeoutException("The Catalogue did not respond in time", failure);
            }
        }
        return new CatalogueUnavailableException("The Catalogue could not be reached", failure);
    }

    private static void mapError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        // The target, never the upstream body: the body may quote values this backend should not repeat.
        String target = request.getMethod() + " " + request.getURI().getPath();
        switch (status) {
            case 403 -> throw new CatalogueForbiddenException("The Catalogue denied " + target);
            case 404 -> throw new CatalogueNotFoundException("The Catalogue has no accessible resource for " + target);
            case 409 -> throw new CatalogueConflictException("The Catalogue reported a conflict for " + target);
            case 422 -> throw new CatalogueUnprocessableException("The Catalogue rejected " + target + " as semantically invalid");
            case 408, 504 -> throw new CatalogueTimeoutException("The Catalogue reported a timeout for " + target);
            default -> {
                if (response.getStatusCode().is5xxServerError()) {
                    throw new CatalogueUnavailableException("The Catalogue failed with status " + status + " for " + target);
                }
                // Any other 4xx means this backend and the Catalogue disagree about the contract —
                // most often a token the Catalogue will not accept, a misconfiguration here rather
                // than something the caller did wrong.
                log.error("Unexpected Catalogue status {} for {}", status, target);
                throw new CatalogueBadResponseException("The Catalogue rejected " + target + " with status " + status);
            }
        }
    }

    private String bearer() {
        return CurrentPrincipal.jwt()
                .map(jwt -> "Bearer " + jwt.getTokenValue())
                .orElseThrow(() -> new IllegalStateException("No JWT in security context"));
    }
}
