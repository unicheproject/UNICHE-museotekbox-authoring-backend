package com.museotek.box.web.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.museotek.box.application.template.CreateExperienceTemplateUseCase;
import com.museotek.box.application.template.DeleteExperienceTemplateUseCase;
import com.museotek.box.application.template.GetExperienceTemplateQuery;
import com.museotek.box.application.template.ListExperienceTemplatesQuery;
import com.museotek.box.application.template.UpdateExperienceTemplateUseCase;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateType;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisations/{orgId}/templates")
public class ExperienceTemplateController {

    // Checked in ExperienceTemplateRules (400 INVALID_TEMPLATE), so an unknown type gets a clear message.
    public record CreateRequest(String type, String name, String description, JsonNode defaults) {
    }

    // A field left out (or null) keeps its value; "description": "" clears it. The type can't change.
    public record UpdateRequest(String name, String description, JsonNode defaults) {
    }

    private final ListExperienceTemplatesQuery listExperienceTemplatesQuery;
    private final GetExperienceTemplateQuery getExperienceTemplateQuery;
    private final CreateExperienceTemplateUseCase createExperienceTemplateUseCase;
    private final UpdateExperienceTemplateUseCase updateExperienceTemplateUseCase;
    private final DeleteExperienceTemplateUseCase deleteExperienceTemplateUseCase;

    public ExperienceTemplateController(
            ListExperienceTemplatesQuery listExperienceTemplatesQuery,
            GetExperienceTemplateQuery getExperienceTemplateQuery,
            CreateExperienceTemplateUseCase createExperienceTemplateUseCase,
            UpdateExperienceTemplateUseCase updateExperienceTemplateUseCase,
            DeleteExperienceTemplateUseCase deleteExperienceTemplateUseCase
    ) {
        this.listExperienceTemplatesQuery = listExperienceTemplatesQuery;
        this.getExperienceTemplateQuery = getExperienceTemplateQuery;
        this.createExperienceTemplateUseCase = createExperienceTemplateUseCase;
        this.updateExperienceTemplateUseCase = updateExperienceTemplateUseCase;
        this.deleteExperienceTemplateUseCase = deleteExperienceTemplateUseCase;
    }

    @Operation(summary = "List the templates the wizard offers this organisation",
            description = "The global templates first, in wizard order, then the organisation's own, by name. "
                    + "Optional type filter (SCAN_AND_DISCOVER, QUIZ, TREASURE_HUNT, GUIDED_PRESENTATION, FROM_SCRATCH).")
    @GetMapping
    public List<ExperienceTemplateResponse> list(
            @PathVariable UUID orgId, @RequestParam(required = false) ExperienceTemplateType type
    ) {
        return listExperienceTemplatesQuery.execute(orgId, type).stream()
                .map(ExperienceTemplateResponse::from)
                .toList();
    }

    @Operation(summary = "Get one template, global or the organisation's own")
    @GetMapping("/{templateId}")
    public ExperienceTemplateResponse get(@PathVariable UUID orgId, @PathVariable UUID templateId) {
        return ExperienceTemplateResponse.from(getExperienceTemplateQuery.execute(orgId, templateId));
    }

    @Operation(summary = "Create one of the organisation's own templates",
            description = "Managers only. {\"type\", \"name\", \"description\", \"defaults\"}: type is one of the template "
                    + "types, defaults a JSON object of the form's starting values (left out = the frontend's built-in ones). "
                    + "400 INVALID_TEMPLATE for an unknown type, a blank or too long name, a description over 1000 characters, "
                    + "or defaults that aren't a JSON object or are over 64 KB.")
    @PostMapping
    public ResponseEntity<ExperienceTemplateResponse> create(@PathVariable UUID orgId, @RequestBody CreateRequest request) {
        ExperienceTemplate template = createExperienceTemplateUseCase.execute(
                orgId, request.type(), request.name(), request.description(), request.defaults());
        return ResponseEntity.status(HttpStatus.CREATED).body(ExperienceTemplateResponse.from(template));
    }

    @Operation(summary = "Change one of the organisation's own templates",
            description = "Managers only. Send only the fields to change; \"description\": \"\" clears it. The type can't "
                    + "change. 403 TEMPLATE_NOT_EDITABLE for a global template. Experiences made from it don't change.")
    @PatchMapping("/{templateId}")
    public ExperienceTemplateResponse update(
            @PathVariable UUID orgId, @PathVariable UUID templateId, @RequestBody UpdateRequest request
    ) {
        ExperienceTemplate template = updateExperienceTemplateUseCase.execute(
                orgId, templateId, request.name(), request.description(), request.defaults());
        return ExperienceTemplateResponse.from(template);
    }

    @Operation(summary = "Delete one of the organisation's own templates",
            description = "Managers only. 403 TEMPLATE_NOT_EDITABLE for a global template. Experiences made from it don't change.")
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> delete(@PathVariable UUID orgId, @PathVariable UUID templateId) {
        deleteExperienceTemplateUseCase.execute(orgId, templateId);
        return ResponseEntity.noContent().build();
    }
}
