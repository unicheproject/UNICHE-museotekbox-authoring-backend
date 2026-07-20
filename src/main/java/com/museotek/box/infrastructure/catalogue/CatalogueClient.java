package com.museotek.box.infrastructure.catalogue;

import com.museotek.box.infrastructure.security.CurrentPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CatalogueClient {

    private final RestClient restClient;

    // Per spec (GET /me/authorization is "cacheable, short-TTL"): keyed by subject, refreshed via
    // If-None-Match once the TTL Catalogue handed back has elapsed. Not a distributed cache — fine
    // for a single instance, and worst case on restart/scale-out is one extra Catalogue round trip.
    private final Map<String, CachedAuthorization> authorizationCache = new ConcurrentHashMap<>();

    private record CachedAuthorization(CatalogueMeAuthorizationDto dto, Instant expiresAt) {}

    public CatalogueClient(@Value("${uniche.catalogue.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public CatalogueProjectDto getProject(UUID projectId) {
        return restClient.get()
                .uri("/api/v1/projects/{id}", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, resp) -> {
                    throw new CatalogueNotFoundException("Project not found or not accessible: " + projectId);
                })
                .body(CatalogueProjectDto.class);
    }

    public CatalogueProjectDto createProject(UUID orgId, CatalogueCreateProjectRequest request) {
        return restClient.post()
                .uri("/api/v1/organisations/{orgId}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(request)
                .retrieve()
                .onStatus(s -> s.value() == 403, (req, resp) -> {
                    throw new CatalogueForbiddenException("Not a manager of organisation: " + orgId);
                })
                .body(CatalogueProjectDto.class);
    }

    public void deleteProject(UUID projectId) {
        restClient.delete()
                .uri("/api/v1/projects/{id}", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, resp) -> {
                    throw new CatalogueNotFoundException("Project not found or not accessible: " + projectId);
                })
                .onStatus(s -> s.value() == 403, (req, resp) -> {
                    throw new CatalogueForbiddenException("Not authorised to delete project: " + projectId);
                })
                .toBodilessEntity();
    }

    public CatalogueMeAuthorizationDto getMeAuthorization() {
        String subject = CurrentPrincipal.subject()
                .orElseThrow(() -> new IllegalStateException("No JWT in security context"));
        Instant now = Instant.now();

        CachedAuthorization cached = authorizationCache.get(subject);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.dto();
        }

        var request = restClient.get()
                .uri("/api/v1/me/authorization")
                .header(HttpHeaders.AUTHORIZATION, bearer());
        if (cached != null) {
            request = request.header(HttpHeaders.IF_NONE_MATCH, cached.dto().etag());
        }

        ResponseEntity<CatalogueMeAuthorizationDto> response =
                request.retrieve().toEntity(CatalogueMeAuthorizationDto.class);

        // A 304 means our cached context is still current — Catalogue sends no body for it, so
        // keep the DTO we already have and just extend its expiry.
        CatalogueMeAuthorizationDto dto = response.getStatusCode() == HttpStatus.NOT_MODIFIED
                ? cached.dto()
                : response.getBody();

        authorizationCache.put(subject, new CachedAuthorization(dto, now.plusSeconds(dto.ttlSeconds())));
        return dto;
    }

    public List<CatalogueProjectDto> listProjectsForOrg(UUID orgId) {
        return restClient.get()
                .uri("/api/v1/organisations/{orgId}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public CatalogueOrganisationDto getOrganisation(UUID orgId) {
        return restClient.get()
                .uri("/api/v1/organisations/{orgId}", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, resp) -> {
                    throw new CatalogueNotFoundException("Organisation not found or not accessible: " + orgId);
                })
                .body(CatalogueOrganisationDto.class);
    }

    // Catalogue's real endpoint is PUT, not PATCH — keep this call as PUT even though our own
    // /api/v1/projects/{id} controller endpoint is exposed as PATCH. Do not "fix" this to PATCH.
    public CatalogueProjectDto updateProject(UUID projectId, CatalogueUpdateProjectRequest request) {
        return restClient.put()
                .uri("/api/v1/projects/{id}", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(request)
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, resp) -> {
                    throw new CatalogueNotFoundException("Project not found or not accessible: " + projectId);
                })
                .onStatus(s -> s.value() == 403, (req, resp) -> {
                    throw new CatalogueForbiddenException("Not authorised to update project: " + projectId);
                })
                .body(CatalogueProjectDto.class);
    }

    public List<CatalogueProjectDto> listDeletedProjectsForOrg(UUID orgId) {
        return restClient.get()
                .uri("/api/v1/organisations/{orgId}/projects/deleted", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(s -> s.value() == 403, (req, resp) -> {
                    throw new CatalogueForbiddenException("Not authorised to list deleted projects for org: " + orgId);
                })
                .body(new ParameterizedTypeReference<>() {});
    }

    public CatalogueProjectDto restoreProject(UUID projectId) {
        return restClient.post()
                .uri("/api/v1/projects/{id}/restore", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, resp) -> {
                    throw new CatalogueNotFoundException("Project not found or not accessible: " + projectId);
                })
                .onStatus(s -> s.value() == 403, (req, resp) -> {
                    throw new CatalogueForbiddenException("Not authorised to restore project: " + projectId);
                })
                .body(CatalogueProjectDto.class);
    }

    private String bearer() {
        return CurrentPrincipal.jwt()
                .map(jwt -> "Bearer " + jwt.getTokenValue())
                .orElseThrow(() -> new IllegalStateException("No JWT in security context"));
    }
}
