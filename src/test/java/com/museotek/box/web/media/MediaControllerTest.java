package com.museotek.box.web.media;

import com.museotek.box.application.media.DeleteMediaUseCase;
import com.museotek.box.application.media.GetMediaContentQuery;
import com.museotek.box.application.media.ListMediaQuery;
import com.museotek.box.application.media.UploadMediaUseCase;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The HTTP side only: multipart binding, response shape, Range handling. The use cases are mocked.
class MediaControllerTest {

    private final UploadMediaUseCase uploadMediaUseCase = mock(UploadMediaUseCase.class);
    private final ListMediaQuery listMediaQuery = mock(ListMediaQuery.class);
    private final GetMediaContentQuery getMediaContentQuery = mock(GetMediaContentQuery.class);
    private final DeleteMediaUseCase deleteMediaUseCase = mock(DeleteMediaUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MediaController(uploadMediaUseCase, listMediaQuery, getMediaContentQuery, deleteMediaUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final UUID orgId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();

    @Test
    void upload_bindsTheFilePart_andAnswers201WithContentUrl() throws Exception {
        when(uploadMediaUseCase.execute(eq(orgId), eq("vase.png"), eq("image/png"), anyLong(), any(InputStream.class)))
                .thenReturn(media("image/png", "vase.png"));
        MockMultipartFile file = new MockMultipartFile("file", "vase.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/organisations/{orgId}/media", orgId).file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(mediaId.toString()))
                .andExpect(jsonPath("$.kind").value("IMAGE"))
                .andExpect(jsonPath("$.contentUrl").value("/api/v1/organisations/" + orgId + "/media/" + mediaId + "/content"));
    }

    @Test
    void upload_withoutFilePart_is400() throws Exception {
        mockMvc.perform(multipart("/api/v1/organisations/{orgId}/media", orgId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPLOAD"));
    }

    @Test
    void content_wholeFile_withTypeAndCaching() throws Exception {
        stubContent("abcdefghij");

        mockMvc.perform(get("/api/v1/organisations/{orgId}/media/{mediaId}/content", orgId, mediaId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, private, immutable"))
                .andExpect(content().string("abcdefghij"));
    }

    @Test
    void content_rangeRequest_answers206WithJustThoseBytes() throws Exception {
        stubContent("abcdefghij");

        mockMvc.perform(get("/api/v1/organisations/{orgId}/media/{mediaId}/content", orgId, mediaId)
                        .header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(content().string("cdef"));
    }

    @Test
    void delete_answers204() throws Exception {
        mockMvc.perform(delete("/api/v1/organisations/{orgId}/media/{mediaId}", orgId, mediaId))
                .andExpect(status().isNoContent());

        verify(deleteMediaUseCase).execute(orgId, mediaId);
    }

    private void stubContent(String bytes) {
        ByteArrayResource file = new ByteArrayResource(bytes.getBytes()) {
            @Override
            public String getFilename() {
                return "sound.mp3";
            }
        };
        when(getMediaContentQuery.execute(orgId, mediaId))
                .thenReturn(new GetMediaContentQuery.MediaContent(media("audio/mpeg", "sound.mp3"), file));
    }

    private Media media(String contentType, String name) {
        Media media = new Media();
        media.setId(mediaId);
        media.setOrgId(orgId);
        media.setKind(contentType.startsWith("image") ? MediaKind.IMAGE : MediaKind.AUDIO);
        media.setName(name);
        media.setContentType(contentType);
        media.setSizeBytes(3);
        media.setUploadedAt(Instant.now());
        return media;
    }
}
