package com.museotek.box.web.error;

import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CatalogueNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorEnvelope handleNotFound(CatalogueNotFoundException e) {
        return new ErrorEnvelope("NOT_FOUND", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(CatalogueForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorEnvelope handleForbidden(CatalogueForbiddenException e) {
        // Unlike 404/400 (expected, routine traffic), 403 is worth a security-audit trail.
        log.warn("Forbidden: {}", e.getMessage());
        return new ErrorEnvelope("FORBIDDEN", e.getMessage(), List.of(), requestId());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorEnvelope handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return new ErrorEnvelope("VALIDATION_ERROR", "Invalid request", details, requestId());
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
