package com.museotek.box.web.organisation;

import com.museotek.box.application.organisation.GetOrganisationQuery;
import com.museotek.box.application.project.CreateProjectUseCase;
import com.museotek.box.application.project.ListDeletedProjectsForOrgQuery;
import com.museotek.box.application.project.ListProjectsForOrgQuery;
import com.museotek.box.infrastructure.catalogue.CatalogueCreateProjectRequest;
import com.museotek.box.web.project.CreateProjectRequest;
import com.museotek.box.web.project.ProjectResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

    private final GetOrganisationQuery getOrganisationQuery;
    private final ListProjectsForOrgQuery listProjectsForOrgQuery;
    private final ListDeletedProjectsForOrgQuery listDeletedProjectsForOrgQuery;
    private final CreateProjectUseCase createProjectUseCase;

    public OrganisationController(
            GetOrganisationQuery getOrganisationQuery,
            ListProjectsForOrgQuery listProjectsForOrgQuery,
            ListDeletedProjectsForOrgQuery listDeletedProjectsForOrgQuery,
            CreateProjectUseCase createProjectUseCase
    ) {
        this.getOrganisationQuery = getOrganisationQuery;
        this.listProjectsForOrgQuery = listProjectsForOrgQuery;
        this.listDeletedProjectsForOrgQuery = listDeletedProjectsForOrgQuery;
        this.createProjectUseCase = createProjectUseCase;
    }

    @GetMapping("/{orgId}")
    public OrganisationResponse getOrganisation(@PathVariable UUID orgId) {
        return OrganisationResponse.from(getOrganisationQuery.execute(orgId));
    }

    @GetMapping("/{orgId}/projects")
    public List<ProjectResponse> listProjects(@PathVariable UUID orgId) {
        return listProjectsForOrgQuery.execute(orgId).stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{orgId}/projects/deleted")
    public List<ProjectResponse> listDeletedProjects(@PathVariable UUID orgId) {
        return listDeletedProjectsForOrgQuery.execute(orgId).stream().map(ProjectResponse::from).toList();
    }

    @PostMapping("/{orgId}/projects")
    public ResponseEntity<ProjectResponse> createProject(@PathVariable UUID orgId, @Valid @RequestBody CreateProjectRequest request) {
        var created = createProjectUseCase.execute(
                orgId,
                new CatalogueCreateProjectRequest(request.name(), request.slug(), request.toolSlug())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(created));
    }
}
