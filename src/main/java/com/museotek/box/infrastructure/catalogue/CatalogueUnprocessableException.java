package com.museotek.box.infrastructure.catalogue;

/**
 * The Catalogue answered 422: it understood the request but rejected it as semantically
 * invalid (e.g. failed a business-rule check). Distinct from this backend's own 400
 * {@code VALIDATION_ERROR} (bean validation on the inbound request) and from
 * {@link CatalogueBadResponseException} (an unexpected 4xx, a contract mismatch rather than
 * a rejected-but-well-formed request).
 */
public class CatalogueUnprocessableException extends RuntimeException {
    public CatalogueUnprocessableException(String message) {
        super(message);
    }
}
