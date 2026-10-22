package com.museotek.box.application.template;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateNotFoundException;
import com.museotek.box.domain.template.TemplateNotEditableException;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Removes one of the organisation's own templates for good. Nothing points at a template
 * (experiences keep their own settings), so deleting one breaks nothing.
 */
@Service
public class DeleteExperienceTemplateUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ExperienceTemplateRepository templateRepository;

    public DeleteExperienceTemplateUseCase(OrgAccessGuard orgAccessGuard, ExperienceTemplateRepository templateRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.templateRepository = templateRepository;
    }

    public void execute(UUID orgId, UUID templateId) {
        orgAccessGuard.requireManager(orgId);

        ExperienceTemplate template = templateRepository.findByIdVisibleTo(templateId, orgId)
                .orElseThrow(() -> new ExperienceTemplateNotFoundException("No template " + templateId + " for org " + orgId));
        if (template.isGlobal()) {
            throw new TemplateNotEditableException("Template " + templateId + " is global and can't be deleted");
        }
        templateRepository.delete(template);
    }
}
