package com.museotek.box.infrastructure.catalogue;

/**
 * The Catalogue did not answer within the configured connect/read timeout (or answered 408/504).
 * Kept distinct from {@link CatalogueUnavailableException} (a genuine outage/other 5xx) so the two
 * failure modes stay separable in status code and logs — no retry-on-timeout is implemented yet,
 * this split only leaves room for one later.
 */
public class CatalogueTimeoutException extends RuntimeException {
    public CatalogueTimeoutException(String message) {
        super(message);
    }

    public CatalogueTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
