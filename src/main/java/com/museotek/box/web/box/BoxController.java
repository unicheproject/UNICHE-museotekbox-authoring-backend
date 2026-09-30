package com.museotek.box.web.box;

import com.museotek.box.application.box.AssignProjectToBoxUseCase;
import com.museotek.box.application.box.CreateBoxUseCase;
import com.museotek.box.application.box.DeleteBoxUseCase;
import com.museotek.box.application.box.GetBoxQuery;
import com.museotek.box.application.box.ListBoxProjectsQuery;
import com.museotek.box.application.box.ListBoxesForOrgQuery;
import com.museotek.box.application.box.UnassignProjectFromBoxUseCase;
import com.museotek.box.application.box.UpdateBoxUseCase;
import com.museotek.box.domain.box.Box;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.web.project.ProjectResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisations/{orgId}/boxes")
public class BoxController {

    private final CreateBoxUseCase createBoxUseCase;
    private final ListBoxesForOrgQuery listBoxesForOrgQuery;
    private final GetBoxQuery getBoxQuery;
    private final UpdateBoxUseCase updateBoxUseCase;
    private final DeleteBoxUseCase deleteBoxUseCase;
    private final ListBoxProjectsQuery listBoxProjectsQuery;
    private final AssignProjectToBoxUseCase assignProjectToBoxUseCase;
    private final UnassignProjectFromBoxUseCase unassignProjectFromBoxUseCase;

    public BoxController(
            CreateBoxUseCase createBoxUseCase,
            ListBoxesForOrgQuery listBoxesForOrgQuery,
            GetBoxQuery getBoxQuery,
            UpdateBoxUseCase updateBoxUseCase,
            DeleteBoxUseCase deleteBoxUseCase,
            ListBoxProjectsQuery listBoxProjectsQuery,
            AssignProjectToBoxUseCase assignProjectToBoxUseCase,
            UnassignProjectFromBoxUseCase unassignProjectFromBoxUseCase
    ) {
        this.createBoxUseCase = createBoxUseCase;
        this.listBoxesForOrgQuery = listBoxesForOrgQuery;
        this.getBoxQuery = getBoxQuery;
        this.updateBoxUseCase = updateBoxUseCase;
        this.deleteBoxUseCase = deleteBoxUseCase;
        this.listBoxProjectsQuery = listBoxProjectsQuery;
        this.assignProjectToBoxUseCase = assignProjectToBoxUseCase;
        this.unassignProjectFromBoxUseCase = unassignProjectFromBoxUseCase;
    }

    @PostMapping
    public ResponseEntity<BoxResponse> createBox(@PathVariable UUID orgId, @Valid @RequestBody CreateBoxRequest request) {
        var created = createBoxUseCase.execute(orgId, request.name(), request.serialNumber());
        BoxResponse response = BoxResponse.from(created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<BoxResponse> listBoxes(@PathVariable UUID orgId) {
        List<Box> boxes = listBoxesForOrgQuery.execute(orgId);
        return boxes.stream()
                .map(BoxResponse::from)
                .toList();
    }

    @GetMapping("/{boxId}")
    public BoxResponse getBox(@PathVariable UUID orgId, @PathVariable Long boxId) {
        Box box = getBoxQuery.execute(orgId, boxId);
        return BoxResponse.from(box);
    }

    @PatchMapping("/{boxId}")
    public BoxResponse updateBox(@PathVariable UUID orgId, @PathVariable Long boxId, @Valid @RequestBody UpdateBoxRequest request) {
        var updated = updateBoxUseCase.execute(orgId, boxId, request.name(), request.serialNumber());
        return BoxResponse.from(updated);
    }

    @DeleteMapping("/{boxId}")
    public ResponseEntity<Void> deleteBox(@PathVariable UUID orgId, @PathVariable Long boxId) {
        deleteBoxUseCase.execute(orgId, boxId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{boxId}/projects")
    public List<ProjectResponse> listBoxProjects(@PathVariable UUID orgId, @PathVariable Long boxId) {
        List<CatalogueProjectDto> projects = listBoxProjectsQuery.execute(orgId, boxId);
        return projects.stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @PutMapping("/{boxId}/projects/{projectId}")
    public ResponseEntity<Void> assignProject(@PathVariable UUID orgId, @PathVariable Long boxId, @PathVariable UUID projectId) {
        assignProjectToBoxUseCase.execute(orgId, boxId, projectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{boxId}/projects/{projectId}")
    public ResponseEntity<Void> unassignProject(@PathVariable UUID orgId, @PathVariable Long boxId, @PathVariable UUID projectId) {
        unassignProjectFromBoxUseCase.execute(orgId, boxId, projectId);
        return ResponseEntity.noContent().build();
    }
}
