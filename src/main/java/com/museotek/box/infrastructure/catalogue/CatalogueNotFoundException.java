package com.museotek.box.infrastructure.catalogue;

public class CatalogueNotFoundException extends RuntimeException {
    public CatalogueNotFoundException(String message) {
        super(message);
    }
}
