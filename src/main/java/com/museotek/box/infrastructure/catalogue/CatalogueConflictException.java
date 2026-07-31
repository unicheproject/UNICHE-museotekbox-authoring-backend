package com.museotek.box.infrastructure.catalogue;

/** The Catalogue answered 409, typically a duplicate project slug. */
public class CatalogueConflictException extends RuntimeException {
    public CatalogueConflictException(String message) {
        super(message);
    }
}
