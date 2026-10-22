package com.museotek.box.web.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.museotek.box.application.template.CreateExperienceTemplateUseCase;
import com.museotek.box.application.template.DeleteExperienceTemplateUseCase;
import com.museotek.box.application.template.GetExperienceTemplateQuery;
import com.museotek.box.application.template.ListExperienceTemplatesQuery;
import com.museotek.box.application.template.UpdateExperienceTemplateUseCase;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateNotFoundException;
import com.museotek.box.domain.template.ExperienceTemplateType;
import com.museotek.box.domain.template.InvalidTemplateException;
import com.museotek.box.domain.template.TemplateNotEditableException;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The HTTP side only: binding, response shape, error codes. The queries are mocked.
class ExperienceTemplateControllerTest {

    private final ListExperienceTemplatesQuery listQuery = mock(ListExperienceTemplatesQuery.class);
    private final GetExperienceTemplateQuery getQuery = mock(GetExperienceTemplateQuery.class);
    private final CreateExperienceTemplateUseCase createUseCase = mock(CreateExperienceTemplateUseCase.class);
    private final UpdateExperienceTemplateUseCase updateUseCase = mock(UpdateExperienceTemplateUseCase.class);
    private final DeleteExperienceTemplateUseCase deleteUseCase = mock(DeleteExperienceTemplateUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ExperienceTemplateController(listQuery, getQuery, createUseCase, updateUseCase, deleteUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID orgId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @Test
    void list_answersTheTemplates_withDefaultsAsAnObject() throws Exception {
        when(listQuery.execute(orgId, null)).thenReturn(List.of(template(null)));

        mockMvc.perform(get("/api/v1/organisations/{orgId}/templates", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(templateId.toString()))
                .andExpect(jsonPath("$[0].type").value("QUIZ"))
                .andExpect(jsonPath("$[0].global").value(true))
                .andExpect(jsonPath("$[0].defaults.questions").value(5));
    }

    @Test
    void list_passesTheTypeFilter() throws Exception {
        when(listQuery.execute(orgId, ExperienceTemplateType.QUIZ)).thenReturn(List.of(template(orgId)));

        mockMvc.perform(get("/api/v1/organisations/{orgId}/templates", orgId).param("type", "QUIZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].global").value(false));
    }

    @Test
    void list_unknownType_is400() throws Exception {
        mockMvc.perform(get("/api/v1/organisations/{orgId}/templates", orgId).param("type", "BINGO"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listQuery);
    }

    @Test
    void get_answersOneTemplate() throws Exception {
        when(getQuery.execute(orgId, templateId)).thenReturn(template(orgId));

        mockMvc.perform(get("/api/v1/organisations/{orgId}/templates/{templateId}", orgId, templateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("School Quiz"))
                .andExpect(jsonPath("$.description").value("Five easy questions"));
    }

    @Test
    void get_notFound_is404WithItsCode() throws Exception {
        when(getQuery.execute(orgId, templateId)).thenThrow(new ExperienceTemplateNotFoundException("no"));

        mockMvc.perform(get("/api/v1/organisations/{orgId}/templates/{templateId}", orgId, templateId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));
    }

    @Test
    void create_passesTheBody_andAnswers201() throws Exception {
        when(createUseCase.execute(eq(orgId), eq("QUIZ"), eq("School Quiz"), isNull(),
                argThat((JsonNode defaults) -> defaults.get("questions").asInt() == 5)))
                .thenReturn(template(orgId));

        mockMvc.perform(post("/api/v1/organisations/{orgId}/templates", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"QUIZ\", \"name\": \"School Quiz\", \"defaults\": {\"questions\": 5}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.global").value(false));
    }

    @Test
    void create_invalid_is400WithItsCode() throws Exception {
        when(createUseCase.execute(eq(orgId), eq("BINGO"), any(), any(), any()))
                .thenThrow(new InvalidTemplateException("Unknown type"));

        mockMvc.perform(post("/api/v1/organisations/{orgId}/templates", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"BINGO\", \"name\": \"Bingo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TEMPLATE"));
    }

    @Test
    void update_passesOnlyWhatWasSent() throws Exception {
        when(updateUseCase.execute(eq(orgId), eq(templateId), eq("Hard Quiz"), isNull(), isNull()))
                .thenReturn(template(orgId));

        mockMvc.perform(patch("/api/v1/organisations/{orgId}/templates/{templateId}", orgId, templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hard Quiz\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void update_globalTemplate_is403WithItsCode() throws Exception {
        when(updateUseCase.execute(eq(orgId), eq(templateId), any(), any(), any()))
                .thenThrow(new TemplateNotEditableException("global"));

        mockMvc.perform(patch("/api/v1/organisations/{orgId}/templates/{templateId}", orgId, templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mine now\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_EDITABLE"));
    }

    @Test
    void delete_answers204() throws Exception {
        mockMvc.perform(delete("/api/v1/organisations/{orgId}/templates/{templateId}", orgId, templateId))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).execute(orgId, templateId);
    }

    @Test
    void delete_globalTemplate_is403() throws Exception {
        doThrow(new TemplateNotEditableException("global")).when(deleteUseCase).execute(orgId, templateId);

        mockMvc.perform(delete("/api/v1/organisations/{orgId}/templates/{templateId}", orgId, templateId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_EDITABLE"));
    }

    private ExperienceTemplate template(UUID org) {
        ExperienceTemplate template = new ExperienceTemplate();
        template.setId(templateId);
        template.setOrgId(org);
        template.setType(ExperienceTemplateType.QUIZ);
        template.setName("School Quiz");
        template.setDescription("Five easy questions");
        template.setDefaults("{\"questions\": 5}");
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return template;
    }
}
