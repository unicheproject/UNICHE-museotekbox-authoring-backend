package com.museotek.box.application.template;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateNotFoundException;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** One template, global or the organisation's own. Another organisation's template is not found. */
@Service
public class GetExperienceTemplateQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final ExperienceTemplateRepository templateRepository;

    public GetExperienceTemplateQuery(OrgAccessGuard orgAccessGuard, ExperienceTemplateRepository templateRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.templateRepository = templateRepository;
    }

    public ExperienceTemplate execute(UUID orgId, UUID templateId) {
        orgAccessGuard.requireAccess(orgId);
        return templateRepository.findByIdVisibleTo(templateId, orgId)
                .orElseThrow(() -> new ExperienceTemplateNotFoundException("No template " + templateId + " for org " + orgId));
    }
}
