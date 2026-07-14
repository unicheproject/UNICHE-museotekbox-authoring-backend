package com.museotek.box.infrastructure.catalogue;

public class CatalogueForbiddenException extends RuntimeException {
    public CatalogueForbiddenException(String message) {
        super(message);
    }
}
