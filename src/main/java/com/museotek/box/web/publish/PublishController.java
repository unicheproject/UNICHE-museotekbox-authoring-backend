package com.museotek.box.web.publish;

import com.museotek.box.application.publish.GetPublishStatusQuery;
import com.museotek.box.application.publish.PublishExperienceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Publishing an experience: a frozen copy of the draft that Boxes play, so curators can keep editing
 * the draft. Which Boxes play it is still the Box assignment ({@code PUT /boxes/{boxId}/projects/{projectId}}).
 */
@Tag(name = "Publishing", description = "Frozen copies of an experience that Boxes play.")
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class PublishController {

    private final PublishExperienceUseCase publishExperienceUseCase;
    private final GetPublishStatusQuery getPublishStatusQuery;

    public PublishController(PublishExperienceUseCase publishExperienceUseCase, GetPublishStatusQuery getPublishStatusQuery) {
        this.publishExperienceUseCase = publishExperienceUseCase;
        this.getPublishStatusQuery = getPublishStatusQuery;
    }

    @Operation(summary = "Publish the experience",
            description = "Org managers only. Saves the current draft as the next published version; every Box the "
                    + "experience is assigned to plays it from then on. If the draft hasn't changed since the last "
                    + "publish, nothing is saved and the current version is returned.")
    @ApiResponse(responseCode = "200", description = "Published (or already up to date)")
    @ApiResponse(responseCode = "403", description = "The caller is not a manager of the project's organisation")
    @ApiResponse(responseCode = "404", description = "No accessible project with this id")
    @ApiResponse(responseCode = "409", description = "START_CARD_IN_USE: another experience on one of its Boxes starts with the same card")
    @ApiResponse(responseCode = "422", description = "EXPERIENCE_NOT_READY: the draft has gaps, listed in details")
    @PostMapping("/publish")
    public PublishStatusResponse publish(@PathVariable UUID projectId) {
        return PublishStatusResponse.from(publishExperienceUseCase.execute(projectId));
    }

    @Operation(summary = "Which version Boxes play",
            description = "The latest published version, and whether the draft has changed since (hasUnpublishedChanges).")
    @ApiResponse(responseCode = "200", description = "The latest published version")
    @ApiResponse(responseCode = "404", description = "EXPERIENCE_NOT_PUBLISHED: never published, or no accessible project with this id")
    @GetMapping("/published")
    public PublishStatusResponse published(@PathVariable UUID projectId) {
        return PublishStatusResponse.from(getPublishStatusQuery.execute(projectId));
    }
}
