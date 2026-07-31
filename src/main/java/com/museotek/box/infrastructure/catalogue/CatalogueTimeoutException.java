package com.museotek.box.infrastructure.catalogue;

/**
 * The Catalogue did not answer within the configured connect/read timeout (or answered 408/504).
 * Distinct from {@link CatalogueUnavailableException} because a timeout is worth retrying and an
 * outage is worth reporting.
 */
public class CatalogueTimeoutException extends RuntimeException {
    public CatalogueTimeoutException(String message) {
        super(message);
    }

    public CatalogueTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
