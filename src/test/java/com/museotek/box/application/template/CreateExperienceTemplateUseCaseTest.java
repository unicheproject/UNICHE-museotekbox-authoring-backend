package com.museotek.box.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.orgaccess.OrgManagerRequiredException;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateType;
import com.museotek.box.domain.template.InvalidTemplateException;
import com.museotek.box.infrastructure.repository.ExperienceTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateExperienceTemplateUseCaseTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final ExperienceTemplateRepository templateRepository = mock(ExperienceTemplateRepository.class);
    private final CreateExperienceTemplateUseCase useCase = new CreateExperienceTemplateUseCase(orgAccessGuard, templateRepository);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void success_savesTheOrgsOwnTemplate() throws Exception {
        when(templateRepository.save(any(ExperienceTemplate.class))).thenAnswer(call -> call.getArgument(0));

        ExperienceTemplate created = useCase.execute(orgId, "QUIZ", "  School Quiz  ", "  Five easy questions ",
                JSON.readTree("{\"questions\": 5}"));

        assertThat(created.getOrgId()).isEqualTo(orgId);
        assertThat(created.isGlobal()).isFalse();
        assertThat(created.getType()).isEqualTo(ExperienceTemplateType.QUIZ);
        assertThat(created.getName()).isEqualTo("School Quiz");
        assertThat(created.getDescription()).isEqualTo("Five easy questions");
        assertThat(created.getDefaults()).isEqualTo("{\"questions\":5}");
        assertThat(created.getCreatedAt()).isNotNull().isEqualTo(created.getUpdatedAt());
    }

    @Test
    void noDefaults_storesAnEmptyObject_andBlankDescription_storesNone() {
        when(templateRepository.save(any(ExperienceTemplate.class))).thenAnswer(call -> call.getArgument(0));

        ExperienceTemplate created = useCase.execute(orgId, "QUIZ", "School Quiz", "   ", null);

        assertThat(created.getDefaults()).isEqualTo("{}");
        assertThat(created.getDescription()).isNull();
    }

    @Test
    void unknownType_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, "BINGO", "Bingo", null, null))
                .isInstanceOf(InvalidTemplateException.class)
                .hasMessageContaining("BINGO");
        verifyNoInteractions(templateRepository);
    }

    @Test
    void missingType_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, null, "School Quiz", null, null))
                .isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void blankOrTooLongName_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, "QUIZ", "  ", null, null))
                .isInstanceOf(InvalidTemplateException.class);
        assertThatThrownBy(() -> useCase.execute(orgId, "QUIZ", "x".repeat(256), null, null))
                .isInstanceOf(InvalidTemplateException.class);
        verifyNoInteractions(templateRepository);
    }

    @Test
    void tooLongDescription_isRefused() {
        assertThatThrownBy(() -> useCase.execute(orgId, "QUIZ", "School Quiz", "x".repeat(1001), null))
                .isInstanceOf(InvalidTemplateException.class);
    }

    @Test
    void defaultsThatArentAnObject_areRefused() throws Exception {
        JsonNode array = JSON.readTree("[1, 2]");

        assertThatThrownBy(() -> useCase.execute(orgId, "QUIZ", "School Quiz", null, array))
                .isInstanceOf(InvalidTemplateException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    void defaultsOver64Kb_areRefused() {
        JsonNode huge = JSON.createObjectNode().put("text", "x".repeat(64 * 1024));

        assertThatThrownBy(() -> useCase.execute(orgId, "QUIZ", "School Quiz", null, huge))
                .isInstanceOf(InvalidTemplateException.class)
                .hasMessageContaining("64 KB");
    }

    @Test
    void notAManager_touchesNothing() {
        doThrow(new OrgManagerRequiredException("no")).when(orgAccessGuard).requireManager(orgId);

        assertThatThrownBy(() -> useCase.execute(orgId, "QUIZ", "School Quiz", null, null))
                .isInstanceOf(OrgManagerRequiredException.class);
        verifyNoInteractions(templateRepository);
    }
}
