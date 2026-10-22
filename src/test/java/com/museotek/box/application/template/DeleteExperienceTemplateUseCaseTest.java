package com.museotek.box.application.template;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateNotFoundException;
import com.museotek.box.domain.template.TemplateNotEditableException;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeleteExperienceTemplateUseCaseTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ExperienceTemplateRepository templateRepository = mock(ExperienceTemplateRepository.class);
    private final DeleteExperienceTemplateUseCase useCase = new DeleteExperienceTemplateUseCase(orgAccessGuard, templateRepository);

    private final UUID orgId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @Test
    void theOrgsOwnTemplate_isDeleted() {
        ExperienceTemplate template = stored(orgId);

        useCase.execute(orgId, templateId);

        verify(templateRepository).delete(template);
    }

    @Test
    void aGlobalTemplate_cantBeDeleted() {
        stored(null);

        assertThatThrownBy(() -> useCase.execute(orgId, templateId)).isInstanceOf(TemplateNotEditableException.class);
        verify(templateRepository, never()).delete(any());
    }

    @Test
    void anotherOrgsTemplate_isNotFound() {
        when(templateRepository.findByIdVisibleTo(templateId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, templateId)).isInstanceOf(ExperienceTemplateNotFoundException.class);
    }

    @Test
    void notAManager_touchesNothing() {
        doThrow(new OrgManagerRequiredException("no")).when(orgAccessGuard).requireManager(orgId);

        assertThatThrownBy(() -> useCase.execute(orgId, templateId)).isInstanceOf(OrgManagerRequiredException.class);
        verifyNoInteractions(templateRepository);
    }

    private ExperienceTemplate stored(UUID org) {
        ExperienceTemplate template = new ExperienceTemplate();
        template.setId(templateId);
        template.setOrgId(org);
        when(templateRepository.findByIdVisibleTo(templateId, orgId)).thenReturn(Optional.of(template));
        return template;
    }
}
