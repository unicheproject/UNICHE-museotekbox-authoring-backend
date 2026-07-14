package com.museotek.box.web.error;

import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
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
        return new ErrorEnvelope("NOT_FOUND", e.getMessage(), List.of());
    }

    @ExceptionHandler(CatalogueForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorEnvelope handleForbidden(CatalogueForbiddenException e) {
        return new ErrorEnvelope("FORBIDDEN", e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorEnvelope handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::toString)
                .toList();
        return new ErrorEnvelope("VALIDATION_ERROR", "Invalid request", details);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorEnvelope handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return new ErrorEnvelope("INTERNAL_ERROR", "An unexpected error occurred", List.of());
    }
}
