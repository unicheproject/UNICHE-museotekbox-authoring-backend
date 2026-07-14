package com.museotek.box.web.project;

import com.museotek.box.application.project.DeleteProjectUseCase;
import com.museotek.box.application.project.GetProjectQuery;
import com.museotek.box.application.project.RestoreProjectUseCase;
import com.museotek.box.application.project.UpdateProjectUseCase;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final GetProjectQuery getProjectQuery;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final DeleteProjectUseCase deleteProjectUseCase;
    private final RestoreProjectUseCase restoreProjectUseCase;

    public ProjectController(
            GetProjectQuery getProjectQuery,
            UpdateProjectUseCase updateProjectUseCase,
            DeleteProjectUseCase deleteProjectUseCase,
            RestoreProjectUseCase restoreProjectUseCase
    ) {
        this.getProjectQuery = getProjectQuery;
        this.updateProjectUseCase = updateProjectUseCase;
        this.deleteProjectUseCase = deleteProjectUseCase;
        this.restoreProjectUseCase = restoreProjectUseCase;
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable UUID id) {
        return ProjectResponse.from(getProjectQuery.execute(id));
    }

    @PatchMapping("/{id}")
    public ProjectResponse updateProject(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
        return ProjectResponse.from(updateProjectUseCase.execute(id, new CatalogueUpdateProjectRequest(request.name())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        deleteProjectUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ProjectResponse restoreProject(@PathVariable UUID id) {
        return ProjectResponse.from(restoreProjectUseCase.execute(id));
    }
}
