package com.museotek.box.web.scanobjecttype;

import com.museotek.box.application.scanobjecttype.CreateScanObjectTypeUseCase;
import com.museotek.box.application.scanobjecttype.DeleteScanObjectTypeUseCase;
import com.museotek.box.application.scanobjecttype.GetScanObjectTypeQuery;
import com.museotek.box.application.scanobjecttype.ListScanObjectTypesForOrgQuery;
import com.museotek.box.application.scanobjecttype.UpdateScanObjectTypeUseCase;
import com.museotek.box.domain.scanobject.ScanObjectType;
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
@RequestMapping("/api/v1/organisations/{orgId}/scan-object-types")
public class ScanObjectTypeController {

    private final CreateScanObjectTypeUseCase createScanObjectTypeUseCase;
    private final ListScanObjectTypesForOrgQuery listScanObjectTypesForOrgQuery;
    private final GetScanObjectTypeQuery getScanObjectTypeQuery;
    private final UpdateScanObjectTypeUseCase updateScanObjectTypeUseCase;
    private final DeleteScanObjectTypeUseCase deleteScanObjectTypeUseCase;

    public ScanObjectTypeController(
            CreateScanObjectTypeUseCase createScanObjectTypeUseCase,
            ListScanObjectTypesForOrgQuery listScanObjectTypesForOrgQuery,
            GetScanObjectTypeQuery getScanObjectTypeQuery,
            UpdateScanObjectTypeUseCase updateScanObjectTypeUseCase,
            DeleteScanObjectTypeUseCase deleteScanObjectTypeUseCase
    ) {
        this.createScanObjectTypeUseCase = createScanObjectTypeUseCase;
        this.listScanObjectTypesForOrgQuery = listScanObjectTypesForOrgQuery;
        this.getScanObjectTypeQuery = getScanObjectTypeQuery;
        this.updateScanObjectTypeUseCase = updateScanObjectTypeUseCase;
        this.deleteScanObjectTypeUseCase = deleteScanObjectTypeUseCase;
    }

    @PostMapping
    public ResponseEntity<ScanObjectTypeResponse> createScanObjectType(
            @PathVariable UUID orgId, @Valid @RequestBody CreateScanObjectTypeRequest request
    ) {
        ScanObjectType created = createScanObjectTypeUseCase.execute(orgId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ScanObjectTypeResponse.from(created));
    }

    @GetMapping
    public List<ScanObjectTypeResponse> listScanObjectTypes(@PathVariable UUID orgId) {
        List<ScanObjectType> types = listScanObjectTypesForOrgQuery.execute(orgId);
        return types.stream()
                .map(ScanObjectTypeResponse::from)
                .toList();
    }

    @GetMapping("/{scanObjectTypeId}")
    public ScanObjectTypeResponse getScanObjectType(@PathVariable UUID orgId, @PathVariable Long scanObjectTypeId) {
        ScanObjectType type = getScanObjectTypeQuery.execute(orgId, scanObjectTypeId);
        return ScanObjectTypeResponse.from(type);
    }

    @PatchMapping("/{scanObjectTypeId}")
    public ScanObjectTypeResponse updateScanObjectType(
            @PathVariable UUID orgId, @PathVariable Long scanObjectTypeId, @Valid @RequestBody UpdateScanObjectTypeRequest request
    ) {
        ScanObjectType updated = updateScanObjectTypeUseCase.execute(orgId, scanObjectTypeId, request.name());
        return ScanObjectTypeResponse.from(updated);
    }

    @DeleteMapping("/{scanObjectTypeId}")
    public ResponseEntity<Void> deleteScanObjectType(@PathVariable UUID orgId, @PathVariable Long scanObjectTypeId) {
        deleteScanObjectTypeUseCase.execute(orgId, scanObjectTypeId);
        return ResponseEntity.noContent().build();
    }
}
