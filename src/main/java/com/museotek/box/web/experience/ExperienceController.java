package com.museotek.box.web.experience;

import com.museotek.box.application.experience.ExperienceDocument;
import com.museotek.box.application.experience.ExperienceView;
import com.museotek.box.application.experience.GetExperienceQuery;
import com.museotek.box.application.experience.SaveExperienceUseCase;
import com.museotek.box.domain.experience.ExperienceValidationException;
import com.museotek.box.domain.experience.StaleExperienceVersionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The <b>content</b> of a project, which MuseotekBox calls its experience: scenes, blocks and
 * rules, loaded and saved as one document with a version ({@code ETag} out, {@code If-Match} in,
 * 409 on conflict).
 *
 * <p>An experience is not a separate resource. It's the content of the project with the same
 * id, which is why it sits under {@code /projects/{projectId}}. The path segment is singular on
 * purpose: every project has exactly one experience, with no experience id and no way to create
 * a second one. Read the URL as "this project's content", not "a project's experiences". It
 * follows its project: it starts empty when the project is created, can't be opened while the
 * project is (soft-)deleted, and comes back with it on restore.
 *
 * <p>The project as an item (name, delete, members) is {@code ProjectController}. All data here
 * lives only in our database. See README, "Project vs Experience".
 */
@Tag(name = "Experience content",
        description = "The content of a project: its scenes, blocks and rules. A project and an experience are "
                + "the same thing. Every project has exactly one experience, so this path is singular: read it as "
                + "\"this project's content\", not \"a project's experiences\". The project as an item (name, "
                + "delete, members) is under /api/v1/projects/{id}.")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/experience")
public class ExperienceController {

    private final GetExperienceQuery getExperienceQuery;
    private final SaveExperienceUseCase saveExperienceUseCase;

    public ExperienceController(GetExperienceQuery getExperienceQuery, SaveExperienceUseCase saveExperienceUseCase) {
        this.getExperienceQuery = getExperienceQuery;
        this.saveExperienceUseCase = saveExperienceUseCase;
    }

    @Operation(
            summary = "Load this project's experience content",
            description = "Returns where it plays (output), the flow (the curator's wizard source), the variables, and the scenes, blocks and "
                    + "rules of the project, plus the document version (also sent as the ETag header) and the next "
                    + "free scene/block/rule keys. One per project: there is no experience id. A new project returns "
                    + "an empty experience with output and flow = null.")
    @ApiResponse(responseCode = "200", description = "The experience content, with its version in the ETag header")
    @ApiResponse(responseCode = "403", description = "The caller can't access this project")
    @ApiResponse(responseCode = "404", description = "No accessible project with this id (or it was deleted)")
    @GetMapping
    public ResponseEntity<ExperienceResponse> getExperience(@PathVariable UUID projectId) {
        ExperienceView view = getExperienceQuery.execute(projectId);
        ExperienceResponse response = ExperienceResponse.from(view);
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(response);
    }

    @Operation(
            summary = "Save this project's experience content",
            description = "Replaces the whole experience (flow, variables, and all scenes, blocks and rules) with the "
                    + "document sent. The flow and the graph are always saved together. output (DISPLAY or BOX) is "
                    + "set by the first save and can't change afterwards. Send "
                    + "If-Match with the version from the last GET or save. If someone else saved in between, the "
                    + "response is 409 with the current document, so the client can reload.")
    @ApiResponse(responseCode = "200", description = "Saved. Returns the stored document and its new version in the ETag header")
    @ApiResponse(responseCode = "400", description = "Invalid document (keys, references, start scene, flow schema version, variables, changed output, rule trigger/condition/effects/destination, media id not in the org's media library or of the wrong kind) or malformed If-Match")
    @ApiResponse(responseCode = "403", description = "The caller can't access this project")
    @ApiResponse(responseCode = "404", description = "No accessible project with this id (or it was deleted)")
    @ApiResponse(responseCode = "409", description = "Stale If-Match: someone else saved first. The body is the current document")
    @PutMapping
    public ResponseEntity<ExperienceResponse> saveExperience(
            @PathVariable UUID projectId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ExperienceWriteRequest request
    ) {
        int ifMatchVersion = parseIfMatchVersion(ifMatch);
        ExperienceDocument document = request.toDocument();
        ExperienceView view = saveExperienceUseCase.execute(projectId, ifMatchVersion, document);
        ExperienceResponse response = ExperienceResponse.from(view);
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(response);
    }

    // Handled here, not in GlobalExceptionHandler: per the document-model proposal, a stale
    // write's response body is the current document itself, not a generic ErrorEnvelope - this
    // controller is the only place that already knows how to build that response.
    @ExceptionHandler(StaleExperienceVersionException.class)
    public ResponseEntity<ExperienceResponse> handleStaleVersion(StaleExperienceVersionException e) {
        ExperienceView view = getExperienceQuery.execute(e.getProjectId());
        ExperienceResponse response = ExperienceResponse.from(view);
        return ResponseEntity.status(HttpStatus.CONFLICT).eTag("\"" + view.version() + "\"").body(response);
    }

    // If-Match arrives quoted (RFC 9110 ETag syntax, matching what GET itself returns) - stripped
    // here rather than pushed onto the client. A malformed value is reported the same way as any
    // other document-write validation problem, instead of falling through to a generic 500.
    private static int parseIfMatchVersion(String ifMatch) {
        String value = ifMatch;
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ExperienceValidationException(
                    List.of("If-Match header '" + ifMatch + "' is not a valid version number"));
        }
    }
}
