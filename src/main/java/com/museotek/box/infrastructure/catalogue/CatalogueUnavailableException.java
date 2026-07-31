package com.museotek.box.infrastructure.catalogue;

/**
 * The Catalogue could not be reached, or answered with a 5xx other than a timeout. Authorization
 * cannot be verified in this state, and no local data may stand in for it.
 */
public class CatalogueUnavailableException extends RuntimeException {
    public CatalogueUnavailableException(String message) {
        super(message);
    }

    public CatalogueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
