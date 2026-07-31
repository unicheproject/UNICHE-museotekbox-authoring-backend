package com.museotek.box.infrastructure.catalogue;

/**
 * The Catalogue answered with a status this backend and the Catalogue disagree about — most often
 * an unexpected 4xx, which is a misconfiguration here rather than something the caller did wrong.
 */
public class CatalogueBadResponseException extends RuntimeException {
    public CatalogueBadResponseException(String message) {
        super(message);
    }
}
