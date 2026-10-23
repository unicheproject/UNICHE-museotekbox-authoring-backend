package com.museotek.box.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import com.museotek.box.infrastructure.security.CurrentPrincipal;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Adds one of the organisation's own templates. Managers only, like boxes: a template is offered
 * to every curator of the organisation. Without defaults, the template opens with the frontend's
 * built-in settings for its type.
 */
@Service
public class CreateExperienceTemplateUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ExperienceTemplateRepository templateRepository;
    private final Clock clock;

    public CreateExperienceTemplateUseCase(OrgAccessGuard orgAccessGuard, ExperienceTemplateRepository templateRepository, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.templateRepository = templateRepository;
        this.clock = clock;
    }

    public ExperienceTemplate execute(UUID orgId, String type, String name, String description, JsonNode defaults) {
        orgAccessGuard.requireManager(orgId);

        ExperienceTemplate template = new ExperienceTemplate();
        template.setId(UUID.randomUUID());
        template.setOrgId(orgId);
        template.setType(ExperienceTemplateRules.requireType(type));
        template.setName(ExperienceTemplateRules.requireName(name));
        template.setDescription(ExperienceTemplateRules.description(description));
        template.setDefaults(ExperienceTemplateRules.isAbsent(defaults) ? "{}" : ExperienceTemplateRules.requireDefaults(defaults));
        template.setCreatedBy(CurrentPrincipal.subject().orElse(null));
        Instant now = Instant.now(clock);
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        return templateRepository.save(template);
    }
}
