package com.museotek.box.application.template;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateType;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The templates the wizard offers an organisation: the global ones first, in the order the
 * wizard lists the types, then the organisation's own, by name. Any member may read them.
 */
@Service
public class ListExperienceTemplatesQuery {

    // Global before own; globals by type order; own by name. Sorted here rather than in SQL, where
    // the type is a plain string and has no order of its own.
    private static final Comparator<ExperienceTemplate> WIZARD_ORDER = Comparator
            .comparing((ExperienceTemplate template) -> !template.isGlobal())
            .thenComparing(template -> template.isGlobal() ? template.getType().ordinal() : 0)
            .thenComparing(ExperienceTemplate::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ExperienceTemplate::getCreatedAt);

    private final OrgAccessGuard orgAccessGuard;
    private final ExperienceTemplateRepository templateRepository;

    public ListExperienceTemplatesQuery(OrgAccessGuard orgAccessGuard, ExperienceTemplateRepository templateRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.templateRepository = templateRepository;
    }

    public List<ExperienceTemplate> execute(UUID orgId, ExperienceTemplateType type) {
        orgAccessGuard.requireAccess(orgId);
        List<ExperienceTemplate> templates = type == null
                ? templateRepository.findVisibleTo(orgId)
                : templateRepository.findVisibleToByType(orgId, type);
        return templates.stream()
                .sorted(WIZARD_ORDER)
                .toList();
    }
}
