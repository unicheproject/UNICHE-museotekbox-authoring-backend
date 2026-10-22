package com.museotek.box.web.template;

import com.museotek.box.application.template.GetExperienceTemplateQuery;
import com.museotek.box.application.template.ListExperienceTemplatesQuery;
import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateNotFoundException;
import com.museotek.box.domain.template.ExperienceTemplateType;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The HTTP side only: binding, response shape, error codes. The queries are mocked.
class ExperienceTemplateControllerTest {

    private final ListExperienceTemplatesQuery listQuery = mock(ListExperienceTemplatesQuery.class);
    private final GetExperienceTemplateQuery getQuery = mock(GetExperienceTemplateQuery.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ExperienceTemplateController(listQuery, getQuery))
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
