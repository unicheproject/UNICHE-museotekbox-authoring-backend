package com.museotek.box.web.scanobject;

import com.museotek.box.application.scanobject.CreateColouredCardUseCase;
import com.museotek.box.application.scanobject.CreatePrintedImageUseCase;
import com.museotek.box.application.scanobject.DeleteScanObjectUseCase;
import com.museotek.box.application.scanobject.GetScanObjectQuery;
import com.museotek.box.application.scanobject.ListScanObjectsForOrgQuery;
import com.museotek.box.application.scanobject.UpdateColouredCardUseCase;
import com.museotek.box.application.scanobject.UpdatePrintedImageUseCase;
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

    public ScanObjectController(
            ListScanObjectsForOrgQuery listScanObjectsForOrgQuery,
            GetScanObjectQuery getScanObjectQuery,
            DeleteScanObjectUseCase deleteScanObjectUseCase,
            CreateColouredCardUseCase createColouredCardUseCase,
            UpdateColouredCardUseCase updateColouredCardUseCase,
            CreatePrintedImageUseCase createPrintedImageUseCase,
            UpdatePrintedImageUseCase updatePrintedImageUseCase
    ) {
        this.listScanObjectsForOrgQuery = listScanObjectsForOrgQuery;
        this.getScanObjectQuery = getScanObjectQuery;
        this.deleteScanObjectUseCase = deleteScanObjectUseCase;
        this.createColouredCardUseCase = createColouredCardUseCase;
        this.updateColouredCardUseCase = updateColouredCardUseCase;
        this.createPrintedImageUseCase = createPrintedImageUseCase;
        this.updatePrintedImageUseCase = updatePrintedImageUseCase;
    }

    @GetMapping
    public List<ScanObjectResponse> listScanObjects(@PathVariable UUID orgId) {
        return listScanObjectsForOrgQuery.execute(orgId).stream().map(ScanObjectResponse::from).toList();
    }

    @GetMapping("/{scanObjectId}")
    public ScanObjectResponse getScanObject(@PathVariable UUID orgId, @PathVariable Long scanObjectId) {
        return ScanObjectResponse.from(getScanObjectQuery.execute(orgId, scanObjectId));
    }

    @DeleteMapping("/{scanObjectId}")
    public ResponseEntity<Void> deleteScanObject(@PathVariable UUID orgId, @PathVariable Long scanObjectId) {
        deleteScanObjectUseCase.execute(orgId, scanObjectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/coloured-cards")
    public ResponseEntity<ScanObjectResponse> createColouredCard(@PathVariable UUID orgId, @Valid @RequestBody CreateColouredCardRequest request) {
        var created = createColouredCardUseCase.execute(orgId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.colour());
        return ResponseEntity.status(HttpStatus.CREATED).body(ScanObjectResponse.from(created));
    }

    @PatchMapping("/coloured-cards/{scanObjectId}")
    public ScanObjectResponse updateColouredCard(@PathVariable UUID orgId, @PathVariable Long scanObjectId, @Valid @RequestBody UpdateColouredCardRequest request) {
        var updated = updateColouredCardUseCase.execute(orgId, scanObjectId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.colour());
        return ScanObjectResponse.from(updated);
    }

    @PostMapping("/printed-images")
    public ResponseEntity<ScanObjectResponse> createPrintedImage(@PathVariable UUID orgId, @Valid @RequestBody CreatePrintedImageRequest request) {
        var created = createPrintedImageUseCase.execute(orgId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.imageUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(ScanObjectResponse.from(created));
    }

    @PatchMapping("/printed-images/{scanObjectId}")
    public ScanObjectResponse updatePrintedImage(@PathVariable UUID orgId, @PathVariable Long scanObjectId, @Valid @RequestBody UpdatePrintedImageRequest request) {
        var updated = updatePrintedImageUseCase.execute(orgId, scanObjectId, request.name(), request.rfidTag(), request.reusable(), request.scanObjectTypeId(), request.imageUrl());
        return ScanObjectResponse.from(updated);
    }
}
