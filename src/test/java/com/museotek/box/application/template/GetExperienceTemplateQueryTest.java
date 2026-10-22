package com.museotek.box.application.template;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetExperienceTemplateQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ExperienceTemplateRepository templateRepository = mock(ExperienceTemplateRepository.class);
    private final GetExperienceTemplateQuery query = new GetExperienceTemplateQuery(orgAccessGuard, templateRepository);

    private final UUID orgId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @Test
    void returnsAVisibleTemplate() {
        ExperienceTemplate template = new ExperienceTemplate();
        when(templateRepository.findByIdVisibleTo(templateId, orgId)).thenReturn(Optional.of(template));

        assertThat(query.execute(orgId, templateId)).isSameAs(template);
    }

    @Test
    void anotherOrgsTemplate_isNotFound() {
        when(templateRepository.findByIdVisibleTo(templateId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(orgId, templateId)).isInstanceOf(ExperienceTemplateNotFoundException.class);
    }

    @Test
    void noOrgAccess_touchesNothing() {
        doThrow(new CatalogueForbiddenException("no")).when(orgAccessGuard).requireAccess(orgId);

        assertThatThrownBy(() -> query.execute(orgId, templateId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(templateRepository);
    }
}
