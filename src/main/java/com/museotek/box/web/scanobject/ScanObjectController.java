package com.museotek.box.web.scanobject;

import com.museotek.box.application.scanobject.DeleteScanObjectUseCase;
import com.museotek.box.application.scanobject.GetScanObjectQuery;
import com.museotek.box.application.scanobject.ListScanObjectsForOrgQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public ScanObjectController(
            ListScanObjectsForOrgQuery listScanObjectsForOrgQuery,
            GetScanObjectQuery getScanObjectQuery,
            DeleteScanObjectUseCase deleteScanObjectUseCase
    ) {
        this.listScanObjectsForOrgQuery = listScanObjectsForOrgQuery;
        this.getScanObjectQuery = getScanObjectQuery;
        this.deleteScanObjectUseCase = deleteScanObjectUseCase;
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
}
