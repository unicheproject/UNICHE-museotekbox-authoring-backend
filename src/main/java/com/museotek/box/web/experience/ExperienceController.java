package com.museotek.box.web.experience;

import com.museotek.box.application.experience.ExperienceDocument;
import com.museotek.box.application.experience.ExperienceView;
import com.museotek.box.application.experience.GetExperienceQuery;
import com.museotek.box.application.experience.SaveExperienceUseCase;
import com.museotek.box.domain.experience.ExperienceValidationException;
import com.museotek.box.domain.experience.StaleExperienceVersionException;
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

@RestController
@RequestMapping("/api/v1/projects/{projectId}/experience")
public class ExperienceController {

    private final GetExperienceQuery getExperienceQuery;
    private final SaveExperienceUseCase saveExperienceUseCase;

    public ExperienceController(GetExperienceQuery getExperienceQuery, SaveExperienceUseCase saveExperienceUseCase) {
        this.getExperienceQuery = getExperienceQuery;
        this.saveExperienceUseCase = saveExperienceUseCase;
    }

    @GetMapping
    public ResponseEntity<ExperienceResponse> getExperience(@PathVariable UUID projectId) {
        ExperienceView view = getExperienceQuery.execute(projectId);
        ExperienceResponse response = ExperienceResponse.from(view);
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(response);
    }

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
