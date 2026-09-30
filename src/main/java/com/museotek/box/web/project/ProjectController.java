package com.museotek.box.web.project;

import com.museotek.box.application.project.DeleteProjectUseCase;
import com.museotek.box.application.project.GetProjectQuery;
import com.museotek.box.application.project.ListProjectMembersPassthroughQuery;
import com.museotek.box.application.project.RestoreProjectUseCase;
import com.museotek.box.application.project.UpdateProjectUseCase;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.catalogue.CatalogueUpdateProjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The project <b>as an item</b>: details, rename, delete, restore, members. Everything here is
 * forwarded to Catalogue.
 *
 * <p>A project is the same thing as an experience. This controller never touches the
 * experience's content (scenes, blocks, rules). That is {@code ExperienceController}, under
 * {@code /projects/{projectId}/experience}. Listing and creating projects live under the org
 * ({@code OrganisationController}), and putting a project on a box under {@code BoxController}.
 * See README, "Project vs Experience".
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final GetProjectQuery getProjectQuery;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final DeleteProjectUseCase deleteProjectUseCase;
    private final RestoreProjectUseCase restoreProjectUseCase;
    private final ListProjectMembersPassthroughQuery listProjectMembersQuery;

    public ProjectController(
            GetProjectQuery getProjectQuery,
            UpdateProjectUseCase updateProjectUseCase,
            DeleteProjectUseCase deleteProjectUseCase,
            RestoreProjectUseCase restoreProjectUseCase,
            ListProjectMembersPassthroughQuery listProjectMembersQuery
    ) {
        this.getProjectQuery = getProjectQuery;
        this.updateProjectUseCase = updateProjectUseCase;
        this.deleteProjectUseCase = deleteProjectUseCase;
        this.restoreProjectUseCase = restoreProjectUseCase;
        this.listProjectMembersQuery = listProjectMembersQuery;
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable UUID id) {
        CatalogueProjectDto project = getProjectQuery.execute(id);
        return ProjectResponse.from(project);
    }

    @PatchMapping("/{id}")
    public ProjectResponse updateProject(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
        CatalogueProjectDto updated = updateProjectUseCase.execute(id, new CatalogueUpdateProjectRequest(request.name()));
        return ProjectResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        deleteProjectUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ProjectResponse restoreProject(@PathVariable UUID id) {
        CatalogueProjectDto restored = restoreProjectUseCase.execute(id);
        return ProjectResponse.from(restored);
    }

    @GetMapping("/{id}/members")
    public List<ProjectMemberResponse> listMembers(@PathVariable UUID id) {
        return listProjectMembersQuery.execute(id).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }
}
