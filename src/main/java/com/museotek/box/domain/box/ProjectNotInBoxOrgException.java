package com.museotek.box.domain.box;

/** The project being assigned to a Box belongs to a different org than the Box. Maps to 422 in GlobalExceptionHandler. */
public class ProjectNotInBoxOrgException extends RuntimeException {
    public ProjectNotInBoxOrgException(String message) {
        super(message);
    }
}
