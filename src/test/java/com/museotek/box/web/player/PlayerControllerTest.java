package com.museotek.box.web.player;

import com.museotek.box.application.player.GetBoxForPlayerQuery;
import com.museotek.box.application.player.GetExperienceForPlayerQuery;
import com.museotek.box.application.player.IdentifyBoxQuery;
import com.museotek.box.application.player.ListOrgBoxesForPlayerQuery;
import com.museotek.box.application.player.PlayerBoxView;
import com.museotek.box.application.player.PlayerExperience;
import com.museotek.box.application.player.PlayerExperienceCopy;
import com.museotek.box.application.player.PlayerScanObject;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerControllerTest {

    private final IdentifyBoxQuery identifyBoxQuery = mock(IdentifyBoxQuery.class);
    private final ListOrgBoxesForPlayerQuery listOrgBoxesQuery = mock(ListOrgBoxesForPlayerQuery.class);
    private final GetBoxForPlayerQuery getBoxQuery = mock(GetBoxForPlayerQuery.class);
    private final GetExperienceForPlayerQuery getExperienceQuery = mock(GetExperienceForPlayerQuery.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PlayerController(identifyBoxQuery, listOrgBoxesQuery, getBoxQuery, getExperienceQuery, 30))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    void identify_answersTheBoxAndItsOrg_butNotTheSerial() throws Exception {
        when(identifyBoxQuery.execute("MB-SECRET-1")).thenReturn(box());

        mockMvc.perform(post("/player/v1/boxes/identify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\":\"MB-SECRET-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                .andExpect(jsonPath("$.name").value("Hall A"))
                .andExpect(jsonPath("$.serialNumber").doesNotExist());
    }

    @Test
    void identify_withoutASerial_is400() throws Exception {
        mockMvc.perform(post("/player/v1/boxes/identify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(identifyBoxQuery);
    }

    @Test
    void identify_anUnknownSerial_is404() throws Exception {
        when(identifyBoxQuery.execute(any())).thenThrow(new BoxNotFoundException("No Box with that serial number"));

        mockMvc.perform(post("/player/v1/boxes/identify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serialNumber\":\"MB-UNKNOWN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOX_NOT_FOUND"));
    }

    @Test
    void orgBoxes_listIdsAndNames_withoutSerials() throws Exception {
        when(listOrgBoxesQuery.execute(orgId)).thenReturn(List.of(box()));

        mockMvc.perform(get("/player/v1/organisations/{orgId}/boxes", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].name").value("Hall A"))
                .andExpect(jsonPath("$[0].serialNumber").doesNotExist());
    }

    @Test
    void box_comesWithExperiencesCardTypesTagsAndThePollInterval() throws Exception {
        ScanObjectType exit = new ScanObjectType();
        exit.setId(9L);
        exit.setName("Exit");
        exit.setExit(true);
        when(getBoxQuery.execute(12L)).thenReturn(new PlayerBoxView(box(),
                List.of(new PlayerExperience(projectId, "Wing", 3, ExperienceOutput.DISPLAY, 7L)),
                List.of(exit),
                List.of(new PlayerScanObject("04A1", 9L))));

        mockMvc.perform(get("/player/v1/boxes/{boxId}", 12L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.box.id").value(12))
                .andExpect(jsonPath("$.box.serialNumber").doesNotExist())
                .andExpect(jsonPath("$.experiences[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.experiences[0].version").value(3))
                .andExpect(jsonPath("$.experiences[0].output").value("DISPLAY"))
                .andExpect(jsonPath("$.experiences[0].startCardTypeId").value(7))
                .andExpect(jsonPath("$.cardTypes[0].exit").value(true))
                .andExpect(jsonPath("$.cardTypes[0].universal").value(false))
                .andExpect(jsonPath("$.scanObjects[0].rfidTag").value("04A1"))
                .andExpect(jsonPath("$.scanObjects[0].cardTypeId").value(9))
                .andExpect(jsonPath("$.pollSeconds").value(30));
    }

    @Test
    void experience_passesTheContentThroughAsJson_withItsMediaList() throws Exception {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        media.setId(mediaId);
        media.setKind(MediaKind.VIDEO);
        media.setContentType("video/mp4");
        media.setSizeBytes(48211311L);
        when(getExperienceQuery.execute(12L, projectId)).thenReturn(new PlayerExperienceCopy(
                new PlayerExperience(projectId, "Wing", 3, ExperienceOutput.DISPLAY, 7L),
                Instant.parse("2026-11-03T10:00:00Z"),
                "{\"contentSchemaVersion\":1,\"scenes\":[]}",
                List.of(media)));

        mockMvc.perform(get("/player/v1/boxes/{boxId}/experiences/{projectId}", 12L, projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.content.contentSchemaVersion").value(1))
                .andExpect(jsonPath("$.content.scenes").isArray())
                .andExpect(jsonPath("$.media[0].id").value(mediaId.toString()))
                .andExpect(jsonPath("$.media[0].kind").value("VIDEO"))
                .andExpect(jsonPath("$.media[0].size").value(48211311));
    }

    private Box box() {
        Box box = new Box();
        box.setId(12L);
        box.setOrgId(orgId);
        box.setName("Hall A");
        box.setSerialNumber("MB-SECRET-1");
        return box;
    }
}
