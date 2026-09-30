package com.museotek.box.application.orgaccess;

/** The caller can reach the org but doesn't manage it (and isn't a platform admin). Maps to 403 in GlobalExceptionHandler. */
public class OrgManagerRequiredException extends RuntimeException {
    public OrgManagerRequiredException(String message) {
        super(message);
    }
}
