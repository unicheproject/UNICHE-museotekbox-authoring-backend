package com.museotek.box.web.error;

import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.DuplicateSerialNumberException;
import com.museotek.box.domain.experience.ExperienceValidationException;
import com.museotek.box.infrastructure.catalogue.CatalogueBadResponseException;
import com.museotek.box.infrastructure.catalogue.CatalogueConflictException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueTimeoutException;
import com.museotek.box.infrastructure.catalogue.CatalogueUnavailableException;
import com.museotek.box.infrastructure.catalogue.CatalogueUnprocessableException;
import com.museotek.box.infrastructure.logging.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives {@link GlobalExceptionHandler} through real Spring MVC dispatch (standalone
 * MockMvc, no full context) so the {@code @ResponseStatus} codes are actually exercised,
 * not just the returned {@link ErrorEnvelope} bodies.
 */
class GlobalExceptionHandlerTest {

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new CatalogueNotFoundException("Project not found or not accessible: 123");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new CatalogueForbiddenException("Not a manager of organisation: 456");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new CatalogueConflictException("The Catalogue reported a conflict for POST /api/v1/organisations/1/projects");
        }

        @GetMapping("/unprocessable")
        void unprocessable() {
            throw new CatalogueUnprocessableException("The Catalogue rejected POST /api/v1/organisations/1/projects as semantically invalid");
        }

        @GetMapping("/timeout")
        void timeout() {
            throw new CatalogueTimeoutException("The Catalogue did not respond in time");
        }

        @GetMapping("/unavailable")
        void unavailable() {
            throw new CatalogueUnavailableException("The Catalogue could not be reached");
        }

        @GetMapping("/bad-response")
        void badResponse() {
            throw new CatalogueBadResponseException("The Catalogue rejected GET /api/v1/projects/1 with status 418");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("something exploded");
        }

        @GetMapping("/box-not-found")
        void boxNotFound() {
            throw new BoxNotFoundException("No box 123 for org 456");
        }

        @GetMapping("/duplicate-serial-number")
        void duplicateSerialNumber() {
            throw new DuplicateSerialNumberException("A box with serial number SN-1 already exists");
        }

        @GetMapping("/experience-validation")
        void experienceValidation() {
            throw new ExperienceValidationException(java.util.List.of(
                    "scene_key '01' is not well-formed (expected a positive integer string)"));
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody ValidatedBody body) {
        }

        @GetMapping("/type-mismatch/{orgId}")
        void typeMismatch(@PathVariable UUID orgId) {
        }

        @GetMapping("/missing-header")
        void missingHeader(@RequestHeader("If-Match") String ifMatch) {
        }

        @GetMapping("/no-resource")
        void noResourceFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "test/no-resource");
        }
    }

    record ValidatedBody(@NotBlank String name) {}

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @BeforeEach
    void seedCorrelationId() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-request-id");
    }

    @AfterEach
    void clearCorrelationId() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Test
    void catalogueNotFoundException_mapsTo404WithNotFoundCode() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Project not found or not accessible: 123"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.requestId").value("test-request-id"));
    }

    @Test
    void noCorrelationIdInMdc_requestIdIsNullInResponse() throws Exception {
        MDC.remove(CorrelationIdFilter.MDC_KEY);

        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void catalogueForbiddenException_mapsTo403WithForbiddenCode() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Not a manager of organisation: 456"));
    }

    @Test
    void catalogueConflictException_mapsTo409WithConflictCode() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void catalogueUnprocessableException_mapsTo422WithUpstreamValidationErrorCode() throws Exception {
        mockMvc.perform(get("/test/unprocessable"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UPSTREAM_VALIDATION_ERROR"));
    }

    @Test
    void catalogueTimeoutException_mapsTo504WithUpstreamTimeoutCode() throws Exception {
        mockMvc.perform(get("/test/timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("UPSTREAM_TIMEOUT"));
    }

    @Test
    void catalogueUnavailableException_mapsTo503WithUpstreamUnavailableCode() throws Exception {
        mockMvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void catalogueBadResponseException_mapsTo502WithUpstreamInvalidResponseCode() throws Exception {
        mockMvc.perform(get("/test/bad-response"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_INVALID_RESPONSE"));
    }

    @Test
    void boxNotFoundException_mapsTo404WithBoxNotFoundCode() throws Exception {
        mockMvc.perform(get("/test/box-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOX_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No box 123 for org 456"));
    }

    @Test
    void duplicateSerialNumberException_mapsTo409WithDuplicateSerialNumberCode() throws Exception {
        mockMvc.perform(get("/test/duplicate-serial-number"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SERIAL_NUMBER"))
                .andExpect(jsonPath("$.message").value("A box with serial number SN-1 already exists"));
    }

    @Test
    void experienceValidationException_mapsTo400WithErrorsAsDetails() throws Exception {
        mockMvc.perform(get("/test/experience-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid experience document"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("scene_key")));
    }

    @Test
    void methodArgumentNotValidException_mapsTo400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType("application/json")
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void methodArgumentTypeMismatchException_mapsTo400WithInvalidParameterCode() throws Exception {
        mockMvc.perform(get("/test/type-mismatch/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'orgId': expected UUID"));
    }

    @Test
    void missingRequestHeaderException_mapsTo400WithMissingHeaderCode() throws Exception {
        mockMvc.perform(get("/test/missing-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.message").value("Required header 'If-Match' is missing"));
    }

    @Test
    void noResourceFoundException_mapsTo404NotUnhandled500() throws Exception {
        mockMvc.perform(get("/test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No endpoint for GET test/no-resource"));
    }

    @Test
    void unexpectedException_mapsTo500WithInternalErrorCode() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.details").isEmpty());
    }
}
