package com.museotek.box.web.error;

import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("something exploded");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody ValidatedBody body) {
        }
    }

    record ValidatedBody(@NotBlank String name) {}

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void catalogueNotFoundException_mapsTo404WithNotFoundCode() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Project not found or not accessible: 123"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void catalogueForbiddenException_mapsTo403WithForbiddenCode() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Not a manager of organisation: 456"));
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
    void unexpectedException_mapsTo500WithInternalErrorCode() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.details").isEmpty());
    }
}
