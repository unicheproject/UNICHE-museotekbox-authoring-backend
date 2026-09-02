package com.museotek.box.web.scanobject;

import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.Draft;
import com.museotek.box.domain.scanobject.PrintedImage;
import com.museotek.box.domain.scanobject.ScanObject;
import com.museotek.box.domain.scanobject.ThreeDPrintedObject;

import java.util.UUID;

public record ScanObjectResponse(
        Long id, UUID orgId, Long scanObjectTypeId, String name, String rfidTag, boolean reusable,
        String kind, String colour, String imageUrl, String modelRef
) {

    public static ScanObjectResponse from(ScanObject scanObject) {
        Long scanObjectTypeId = scanObject.getScanObjectType() != null ? scanObject.getScanObjectType().getId() : null;
        if (scanObject instanceof ColouredCard colouredCard) {
            return of(scanObject, scanObjectTypeId, "COLOURED_CARD", colouredCard.getColour().name(), null, null);
        }
        if (scanObject instanceof PrintedImage printedImage) {
            return of(scanObject, scanObjectTypeId, "PRINTED_IMAGE", null, printedImage.getImageUrl(), null);
        }
        if (scanObject instanceof ThreeDPrintedObject threeDPrintedObject) {
            return of(scanObject, scanObjectTypeId, "THREE_D_PRINTED_OBJECT", null, null, threeDPrintedObject.getModelRef());
        }
        if (scanObject instanceof Draft) {
            return of(scanObject, scanObjectTypeId, "DRAFT", null, null, null);
        }
        throw new IllegalStateException("Unknown ScanObject subtype: " + scanObject.getClass());
    }

    private static ScanObjectResponse of(ScanObject scanObject, Long scanObjectTypeId, String kind, String colour, String imageUrl, String modelRef) {
        return new ScanObjectResponse(scanObject.getId(), scanObject.getOrgId(), scanObjectTypeId, scanObject.getName(),
                scanObject.getRfidTag(), scanObject.isReusable(), kind, colour, imageUrl, modelRef);
    }
}
