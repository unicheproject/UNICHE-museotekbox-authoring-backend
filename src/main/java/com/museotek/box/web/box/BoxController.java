package com.museotek.box.web.box;

import com.museotek.box.application.box.CreateBoxUseCase;
import com.museotek.box.application.box.GetBoxQuery;
import com.museotek.box.application.box.ListBoxesForOrgQuery;
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
@RequestMapping("/api/v1/organisations/{orgId}/boxes")
public class BoxController {

    private final CreateBoxUseCase createBoxUseCase;
    private final ListBoxesForOrgQuery listBoxesForOrgQuery;
    private final GetBoxQuery getBoxQuery;

    public BoxController(
            CreateBoxUseCase createBoxUseCase,
            ListBoxesForOrgQuery listBoxesForOrgQuery,
            GetBoxQuery getBoxQuery
    ) {
        this.createBoxUseCase = createBoxUseCase;
        this.listBoxesForOrgQuery = listBoxesForOrgQuery;
        this.getBoxQuery = getBoxQuery;
    }

    @PostMapping
    public ResponseEntity<BoxResponse> createBox(@PathVariable UUID orgId, @Valid @RequestBody CreateBoxRequest request) {
        var created = createBoxUseCase.execute(orgId, request.name(), request.serialNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(BoxResponse.from(created));
    }

    @GetMapping
    public List<BoxResponse> listBoxes(@PathVariable UUID orgId) {
        return listBoxesForOrgQuery.execute(orgId).stream().map(BoxResponse::from).toList();
    }

    @GetMapping("/{boxId}")
    public BoxResponse getBox(@PathVariable UUID orgId, @PathVariable Long boxId) {
        return BoxResponse.from(getBoxQuery.execute(orgId, boxId));
    }
}
