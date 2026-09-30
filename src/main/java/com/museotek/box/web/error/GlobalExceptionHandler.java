package com.museotek.box.web.error;

import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.DuplicateSerialNumberException;
import com.museotek.box.domain.box.ProjectNotInBoxOrgException;
import com.museotek.box.domain.experience.ExperienceValidationException;
import com.museotek.box.infrastructure.catalogue.CatalogueBadResponseException;
import com.museotek.box.infrastructure.catalogue.CatalogueConflictException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueTimeoutException;
import com.museotek.box.infrastructure.catalogue.CatalogueUnavailableException;
import com.museotek.box.infrastructure.catalogue.CatalogueUnprocessableException;
import com.museotek.box.domain.scanobject.DuplicateRfidTagException;
import com.museotek.box.domain.scanobject.ScanObjectNotFoundException;
import com.museotek.box.domain.scanobject.ScanObjectTypeInUseException;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.infrastructure.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CatalogueNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorEnvelope handleNotFound(CatalogueNotFoundException e) {
        return new ErrorEnvelope("NOT_FOUND", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(BoxNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorEnvelope handleBoxNotFound(BoxNotFoundException e) {
        return new ErrorEnvelope("BOX_NOT_FOUND", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(DuplicateSerialNumberException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorEnvelope handleDuplicateSerialNumber(DuplicateSerialNumberException e) {
        return new ErrorEnvelope("DUPLICATE_SERIAL_NUMBER", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(ProjectNotInBoxOrgException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorEnvelope handleProjectNotInBoxOrg(ProjectNotInBoxOrgException e) {
        return new ErrorEnvelope("PROJECT_NOT_IN_BOX_ORG", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(ScanObjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorEnvelope handleScanObjectNotFound(ScanObjectNotFoundException e) {
        return new ErrorEnvelope("SCAN_OBJECT_NOT_FOUND", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(DuplicateRfidTagException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorEnvelope handleDuplicateRfidTag(DuplicateRfidTagException e) {
        return new ErrorEnvelope("DUPLICATE_RFID_TAG", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(ScanObjectTypeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorEnvelope handleScanObjectTypeNotFound(ScanObjectTypeNotFoundException e) {
        return new ErrorEnvelope("SCAN_OBJECT_TYPE_NOT_FOUND", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(ScanObjectTypeInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorEnvelope handleScanObjectTypeInUse(ScanObjectTypeInUseException e) {
        return new ErrorEnvelope("SCAN_OBJECT_TYPE_IN_USE", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(CatalogueForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorEnvelope handleForbidden(CatalogueForbiddenException e) {
        // Unlike 404/400 (expected, routine traffic), 403 is worth a security-audit trail.
        log.warn("Forbidden: {}", e.getMessage());
        return new ErrorEnvelope("FORBIDDEN", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(OrgManagerRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorEnvelope handleOrgManagerRequired(OrgManagerRequiredException e) {
        log.warn("Forbidden: {}", e.getMessage());
        return new ErrorEnvelope("FORBIDDEN", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(CatalogueConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorEnvelope handleConflict(CatalogueConflictException e) {
        return new ErrorEnvelope("CONFLICT", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(CatalogueUnprocessableException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorEnvelope handleUnprocessable(CatalogueUnprocessableException e) {
        return new ErrorEnvelope("UPSTREAM_VALIDATION_ERROR", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(CatalogueTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public ErrorEnvelope handleTimeout(CatalogueTimeoutException e) {
        log.error("Catalogue timed out", e);
        return new ErrorEnvelope("UPSTREAM_TIMEOUT", "The UNICHE Catalogue did not respond in time.", List.of(), requestId());
    }

    @ExceptionHandler(CatalogueUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorEnvelope handleUnavailable(CatalogueUnavailableException e) {
        log.error("Catalogue unavailable, authorisation cannot be verified", e);
        return new ErrorEnvelope("UPSTREAM_UNAVAILABLE", "The UNICHE Catalogue is unavailable, so access cannot be verified.", List.of(), requestId());
    }

    @ExceptionHandler(CatalogueBadResponseException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorEnvelope handleBadResponse(CatalogueBadResponseException e) {
        log.error("Catalogue returned an unexpected response: {}", e.getMessage());
        return new ErrorEnvelope("UPSTREAM_INVALID_RESPONSE", "The UNICHE Catalogue returned a response this backend cannot use.", List.of(), requestId());
    }

    @ExceptionHandler(ExperienceValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorEnvelope handleExperienceValidation(ExperienceValidationException e) {
        return new ErrorEnvelope("VALIDATION_ERROR", "Invalid experience document", e.getErrors(), requestId());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorEnvelope handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return new ErrorEnvelope("VALIDATION_ERROR", "Invalid request", details, requestId());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorEnvelope handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        // Don't echo the rejected value back (same rationale as handleValidation above).
        String expectedType;
        if (e.getRequiredType() != null) {
            expectedType = e.getRequiredType().getSimpleName();
        } else {
            expectedType = "a different type";
        }
        String message = "Invalid value for parameter '" + e.getName() + "': expected " + expectedType;
        return new ErrorEnvelope("INVALID_PARAMETER", message, List.of(), requestId());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorEnvelope handleNoResourceFound(NoResourceFoundException e) {
        // Without this, an unmapped route (typo, method the path doesn't support, an endpoint
        // that isn't built yet) falls through to handleUnexpected below and 500s instead of the
        // clean 404 Spring itself intends for this exception.
        return new ErrorEnvelope("NOT_FOUND", "No endpoint for " + e.getHttpMethod() + " " + e.getResourcePath(), List.of(), requestId());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorEnvelope handleMissingHeader(MissingRequestHeaderException e) {
        return new ErrorEnvelope("MISSING_HEADER", "Required header '" + e.getHeaderName() + "' is missing", List.of(), requestId());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorEnvelope handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return new ErrorEnvelope("INTERNAL_ERROR", "An unexpected error occurred", List.of(), requestId());
    }

    private static String requestId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
