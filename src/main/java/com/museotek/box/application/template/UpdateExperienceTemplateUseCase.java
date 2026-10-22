package com.museotek.box.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateNotFoundException;
import com.museotek.box.domain.template.TemplateNotEditableException;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Changes one of the organisation's own templates. Only the fields sent change; an empty
 * description clears it. The type can't change: a different type needs a different form, so that
 * is a new template. Experiences made from the template keep their own settings and don't change.
 */
@Service
public class UpdateExperienceTemplateUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ExperienceTemplateRepository templateRepository;

    public UpdateExperienceTemplateUseCase(OrgAccessGuard orgAccessGuard, ExperienceTemplateRepository templateRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.templateRepository = templateRepository;
    }

    public ExperienceTemplate execute(UUID orgId, UUID templateId, String name, String description, JsonNode defaults) {
        orgAccessGuard.requireManager(orgId);

        ExperienceTemplate template = templateRepository.findByIdVisibleTo(templateId, orgId)
                .orElseThrow(() -> new ExperienceTemplateNotFoundException("No template " + templateId + " for org " + orgId));
        if (template.isGlobal()) {
            throw new TemplateNotEditableException("Template " + templateId + " is global and can't be changed");
        }

        if (name != null) {
            template.setName(ExperienceTemplateRules.requireName(name));
        }
        if (description != null) {
            template.setDescription(ExperienceTemplateRules.description(description));
        }
        if (!ExperienceTemplateRules.isAbsent(defaults)) {
            template.setDefaults(ExperienceTemplateRules.requireDefaults(defaults));
        }
        template.setUpdatedAt(Instant.now());
        return templateRepository.save(template);
    }
}
