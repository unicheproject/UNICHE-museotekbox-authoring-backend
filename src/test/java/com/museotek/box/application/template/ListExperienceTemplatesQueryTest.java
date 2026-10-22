package com.museotek.box.application.template;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateType;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListExperienceTemplatesQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ExperienceTemplateRepository templateRepository = mock(ExperienceTemplateRepository.class);
    private final ListExperienceTemplatesQuery query = new ListExperienceTemplatesQuery(orgAccessGuard, templateRepository);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void globalsComeFirstInWizardOrder_thenTheOrgsOwnByName() {
        when(templateRepository.findVisibleTo(orgId)).thenReturn(List.of(
                template(orgId, ExperienceTemplateType.QUIZ, "school quiz"),
                template(null, ExperienceTemplateType.FROM_SCRATCH, "Start from scratch"),
                template(orgId, ExperienceTemplateType.TREASURE_HUNT, "Garden hunt"),
                template(null, ExperienceTemplateType.QUIZ, "Quiz"),
                template(null, ExperienceTemplateType.SCAN_AND_DISCOVER, "Scan & Discover")));

        List<ExperienceTemplate> result = query.execute(orgId, null);

        assertThat(result).extracting(ExperienceTemplate::getName)
                .containsExactly("Scan & Discover", "Quiz", "Start from scratch", "Garden hunt", "school quiz");
    }

    @Test
    void aTypeFilter_usesTheTypeQuery() {
        ExperienceTemplate quiz = template(null, ExperienceTemplateType.QUIZ, "Quiz");
        when(templateRepository.findVisibleToByType(orgId, ExperienceTemplateType.QUIZ)).thenReturn(List.of(quiz));

        assertThat(query.execute(orgId, ExperienceTemplateType.QUIZ)).containsExactly(quiz);
    }

    @Test
    void noOrgAccess_touchesNothing() {
        doThrow(new CatalogueForbiddenException("no")).when(orgAccessGuard).requireAccess(orgId);

        assertThatThrownBy(() -> query.execute(orgId, null)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(templateRepository);
    }

    private static ExperienceTemplate template(UUID org, ExperienceTemplateType type, String name) {
        ExperienceTemplate template = new ExperienceTemplate();
        template.setId(UUID.randomUUID());
        template.setOrgId(org);
        template.setType(type);
        template.setName(name);
        template.setDefaults("{}");
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }
}
