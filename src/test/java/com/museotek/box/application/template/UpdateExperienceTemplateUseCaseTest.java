package com.museotek.box.application.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateNotFoundException;
import com.museotek.box.domain.template.ExperienceTemplateType;
import com.museotek.box.domain.template.InvalidTemplateException;
import com.museotek.box.domain.template.TemplateNotEditableException;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UpdateExperienceTemplateUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant CREATED = Instant.parse("2026-10-01T10:00:00Z");

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ExperienceTemplateRepository templateRepository = mock(ExperienceTemplateRepository.class);
    private final UpdateExperienceTemplateUseCase useCase = new UpdateExperienceTemplateUseCase(orgAccessGuard, templateRepository, CLOCK);

    private final UUID orgId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @Test
    void onlyTheFieldsSent_change() throws Exception {
        ExperienceTemplate template = stored(orgId);

        ExperienceTemplate updated = useCase.execute(orgId, templateId, null, null, JSON.readTree("{\"questions\": 7}"));

        assertThat(updated.getName()).isEqualTo("School Quiz");
        assertThat(updated.getDescription()).isEqualTo("Five easy questions");
        assertThat(updated.getDefaults()).isEqualTo("{\"questions\":7}");
        assertThat(updated.getType()).isEqualTo(ExperienceTemplateType.QUIZ);
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
        verify(templateRepository).save(template);
    }

    @Test
    void anEmptyDescription_clearsIt_andTheNameIsTrimmed() {
        stored(orgId);

        ExperienceTemplate updated = useCase.execute(orgId, templateId, "  Hard Quiz ", "", null);

        assertThat(updated.getName()).isEqualTo("Hard Quiz");
        assertThat(updated.getDescription()).isNull();
        assertThat(updated.getDefaults()).isEqualTo("{\"questions\":5}");
    }

    @Test
    void aBlankName_isRefused() {
        stored(orgId);

        assertThatThrownBy(() -> useCase.execute(orgId, templateId, " ", null, null))
                .isInstanceOf(InvalidTemplateException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void aGlobalTemplate_cantBeChanged() {
        stored(null);

        assertThatThrownBy(() -> useCase.execute(orgId, templateId, "Mine now", null, null))
                .isInstanceOf(TemplateNotEditableException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void anotherOrgsTemplate_isNotFound() {
        when(templateRepository.findByIdVisibleTo(templateId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orgId, templateId, "Mine now", null, null))
                .isInstanceOf(ExperienceTemplateNotFoundException.class);
    }

    @Test
    void notAManager_touchesNothing() {
        doThrow(new OrgManagerRequiredException("no")).when(orgAccessGuard).requireManager(orgId);

        assertThatThrownBy(() -> useCase.execute(orgId, templateId, "Mine now", null, null))
                .isInstanceOf(OrgManagerRequiredException.class);
        verifyNoInteractions(templateRepository);
    }

    private ExperienceTemplate stored(UUID org) {
        ExperienceTemplate template = new ExperienceTemplate();
        template.setId(templateId);
        template.setOrgId(org);
        template.setType(ExperienceTemplateType.QUIZ);
        template.setName("School Quiz");
        template.setDescription("Five easy questions");
        template.setDefaults("{\"questions\":5}");
        template.setCreatedAt(CREATED);
        template.setUpdatedAt(CREATED);
        when(templateRepository.findByIdVisibleTo(templateId, orgId)).thenReturn(Optional.of(template));
        when(templateRepository.save(template)).thenReturn(template);
        return template;
    }
}
