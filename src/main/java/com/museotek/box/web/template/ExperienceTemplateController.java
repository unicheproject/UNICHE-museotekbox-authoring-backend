package com.museotek.box.web.template;

import com.museotek.box.application.template.GetExperienceTemplateQuery;
import com.museotek.box.application.template.ListExperienceTemplatesQuery;
import com.museotek.box.domain.template.ExperienceTemplateType;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisations/{orgId}/templates")
public class ExperienceTemplateController {

    private final ListExperienceTemplatesQuery listExperienceTemplatesQuery;
    private final GetExperienceTemplateQuery getExperienceTemplateQuery;

    public ExperienceTemplateController(
            ListExperienceTemplatesQuery listExperienceTemplatesQuery,
            GetExperienceTemplateQuery getExperienceTemplateQuery
    ) {
        this.listExperienceTemplatesQuery = listExperienceTemplatesQuery;
        this.getExperienceTemplateQuery = getExperienceTemplateQuery;
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
}
