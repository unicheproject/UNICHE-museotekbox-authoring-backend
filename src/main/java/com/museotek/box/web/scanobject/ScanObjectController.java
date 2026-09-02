package com.museotek.box.web.scanobject;

import com.museotek.box.application.scanobject.CreateColouredCardUseCase;
import com.museotek.box.application.scanobject.CreatePrintedImageUseCase;
import com.museotek.box.application.scanobject.CreateDraftUseCase;
import com.museotek.box.application.scanobject.CreateThreeDPrintedObjectUseCase;
import com.museotek.box.application.scanobject.DeleteScanObjectUseCase;
import com.museotek.box.application.scanobject.GetScanObjectQuery;
import com.museotek.box.application.scanobject.ListScanObjectsForOrgQuery;
import com.museotek.box.application.scanobject.UpdateColouredCardUseCase;
import com.museotek.box.application.scanobject.UpdateDraftUseCase;
import com.museotek.box.application.scanobject.UpdatePrintedImageUseCase;
import com.museotek.box.application.scanobject.UpdateThreeDPrintedObjectUseCase;
import com.museotek.box.domain.scanobject.ScanObject;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/organisations/{orgId}/scan-objects")
public class ScanObjectController {

    private final ListScanObjectsForOrgQuery listScanObjectsForOrgQuery;
    private final GetScanObjectQuery getScanObjectQuery;
    private final DeleteScanObjectUseCase deleteScanObjectUseCase;
    private final CreateColouredCardUseCase createColouredCardUseCase;
    private final UpdateColouredCardUseCase updateColouredCardUseCase;
    private final CreatePrintedImageUseCase createPrintedImageUseCase;
    private final UpdatePrintedImageUseCase updatePrintedImageUseCase;
    private final CreateThreeDPrintedObjectUseCase createThreeDPrintedObjectUseCase;
    private final UpdateThreeDPrintedObjectUseCase updateThreeDPrintedObjectUseCase;
    private final CreateDraftUseCase createDraftUseCase;
    private final UpdateDraftUseCase updateDraftUseCase;

    public ScanObjectController(
            ListScanObjectsForOrgQuery listScanObjectsForOrgQuery,
            GetScanObjectQuery getScanObjectQuery,
            DeleteScanObjectUseCase deleteScanObjectUseCase,
            CreateColouredCardUseCase createColouredCardUseCase,
            UpdateColouredCardUseCase updateColouredCardUseCase,
            CreatePrintedImageUseCase createPrintedImageUseCase,
            UpdatePrintedImageUseCase updatePrintedImageUseCase,
            CreateThreeDPrintedObjectUseCase createThreeDPrintedObjectUseCase,
            UpdateThreeDPrintedObjectUseCase updateThreeDPrintedObjectUseCase,
            CreateDraftUseCase createDraftUseCase,
            UpdateDraftUseCase updateDraftUseCase
    ) {
        this.listScanObjectsForOrgQuery = listScanObjectsForOrgQuery;
        this.getScanObjectQuery = getScanObjectQuery;
        this.deleteScanObjectUseCase = deleteScanObjectUseCase;
        this.createColouredCardUseCase = createColouredCardUseCase;
        this.updateColouredCardUseCase = updateColouredCardUseCase;
        this.createPrintedImageUseCase = createPrintedImageUseCase;
        this.updatePrintedImageUseCase = updatePrintedImageUseCase;
        this.createThreeDPrintedObjectUseCase = createThreeDPrintedObjectUseCase;
        this.updateThreeDPrintedObjectUseCase = updateThreeDPrintedObjectUseCase;
        this.createDraftUseCase = createDraftUseCase;
        this.updateDraftUseCase = updateDraftUseCase;
    }

    @GetMapping
    public List<ScanObjectResponse> listScanObjects(@PathVariable UUID orgId) {
        List<ScanObject> scanObjects = listScanObjectsForOrgQuery.execute(orgId);
        return scanObjects.stream()
                .map(ScanObjectResponse::from)
                .toList();
    }

    @GetMapping("/{scanObjectId}")
    public ScanObjectResponse getScanObject(@PathVariable UUID orgId, @PathVariable Long scanObjectId) {
        ScanObject scanObject = getScanObjectQuery.execute(orgId, scanObjectId);
        return ScanObjectResponse.from(scanObject);
    }

    @DeleteMapping("/{scanObjectId}")
    public ResponseEntity<Void> deleteScanObject(@PathVariable UUID orgId, @PathVariable Long scanObjectId) {
        deleteScanObjectUseCase.execute(orgId, scanObjectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/coloured-cards")
    public ResponseEntity<ScanObjectResponse> createColouredCard(@PathVariable UUID orgId, @Valid @RequestBody CreateColouredCardRequest request) {
        var created = createColouredCardUseCase.execute(orgId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.colour());
        ScanObjectResponse response = ScanObjectResponse.from(created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/coloured-cards/{scanObjectId}")
    public ScanObjectResponse updateColouredCard(@PathVariable UUID orgId, @PathVariable Long scanObjectId, @Valid @RequestBody UpdateColouredCardRequest request) {
        var updated = updateColouredCardUseCase.execute(orgId, scanObjectId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.colour());
        return ScanObjectResponse.from(updated);
    }

    @PostMapping("/printed-images")
    public ResponseEntity<ScanObjectResponse> createPrintedImage(@PathVariable UUID orgId, @Valid @RequestBody CreatePrintedImageRequest request) {
        var created = createPrintedImageUseCase.execute(orgId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.imageUrl());
        ScanObjectResponse response = ScanObjectResponse.from(created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/printed-images/{scanObjectId}")
    public ScanObjectResponse updatePrintedImage(@PathVariable UUID orgId, @PathVariable Long scanObjectId, @Valid @RequestBody UpdatePrintedImageRequest request) {
        var updated = updatePrintedImageUseCase.execute(orgId, scanObjectId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.imageUrl());
        return ScanObjectResponse.from(updated);
    }

    @PostMapping("/three-d-printed-objects")
    public ResponseEntity<ScanObjectResponse> createThreeDPrintedObject(@PathVariable UUID orgId, @Valid @RequestBody CreateThreeDPrintedObjectRequest request) {
        var created = createThreeDPrintedObjectUseCase.execute(orgId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.modelRef());
        ScanObjectResponse response = ScanObjectResponse.from(created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/three-d-printed-objects/{scanObjectId}")
    public ScanObjectResponse updateThreeDPrintedObject(@PathVariable UUID orgId, @PathVariable Long scanObjectId, @Valid @RequestBody UpdateThreeDPrintedObjectRequest request) {
        var updated = updateThreeDPrintedObjectUseCase.execute(orgId, scanObjectId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.modelRef());
        return ScanObjectResponse.from(updated);
    }

    @PostMapping("/drafts")
    public ResponseEntity<ScanObjectResponse> createDraft(@PathVariable UUID orgId, @Valid @RequestBody CreateDraftRequest request) {
        var created = createDraftUseCase.execute(orgId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId());
        ScanObjectResponse response = ScanObjectResponse.from(created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/drafts/{scanObjectId}")
    public ScanObjectResponse updateDraft(@PathVariable UUID orgId, @PathVariable Long scanObjectId, @Valid @RequestBody UpdateDraftRequest request) {
        var updated = updateDraftUseCase.execute(orgId, scanObjectId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId());
        return ScanObjectResponse.from(updated);
    }
}
