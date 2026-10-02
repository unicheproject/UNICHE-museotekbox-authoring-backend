package com.museotek.box.web.media;

import com.museotek.box.application.media.DeleteMediaUseCase;
import com.museotek.box.application.media.GetMediaContentQuery;
import com.museotek.box.application.media.ListMediaQuery;
import com.museotek.box.application.media.UploadMediaUseCase;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "Media library",
        description = "An organisation's pictures, videos and sounds, uploaded once and used in any of its experiences.")
@RestController
@RequestMapping("/api/v1/organisations/{orgId}/media")
public class MediaController {

    private final UploadMediaUseCase uploadMediaUseCase;
    private final ListMediaQuery listMediaQuery;
    private final GetMediaContentQuery getMediaContentQuery;
    private final DeleteMediaUseCase deleteMediaUseCase;

    public MediaController(
            UploadMediaUseCase uploadMediaUseCase,
            ListMediaQuery listMediaQuery,
            GetMediaContentQuery getMediaContentQuery,
            DeleteMediaUseCase deleteMediaUseCase
    ) {
        this.uploadMediaUseCase = uploadMediaUseCase;
        this.listMediaQuery = listMediaQuery;
        this.getMediaContentQuery = getMediaContentQuery;
        this.deleteMediaUseCase = deleteMediaUseCase;
    }

    @Operation(summary = "Upload a file to the media library",
            description = "multipart/form-data with the file in the 'file' field. Allowed: PNG, JPEG, WebP images; "
                    + "MP3, WAV, OGG audio; MP4 video, each kind with its own size limit. 413 if too large, 415 if the "
                    + "type isn't allowed or the content doesn't match it.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> upload(
            @PathVariable UUID orgId, @RequestParam("file") MultipartFile file, HttpServletRequest request
    ) {
        Media media;
        try (InputStream content = file.getInputStream()) {
            media = uploadMediaUseCase.execute(orgId, file.getOriginalFilename(), file.getContentType(), file.getSize(), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(MediaResponse.from(media, request.getContextPath()));
    }

    @Operation(summary = "List the media library, newest first",
            description = "Optional kind filter (IMAGE, VIDEO or AUDIO), as used by the editor's pickers.")
    @GetMapping
    public List<MediaResponse> list(
            @PathVariable UUID orgId, @RequestParam(required = false) MediaKind kind, HttpServletRequest request
    ) {
        List<Media> media = listMediaQuery.execute(orgId, kind);
        return media.stream()
                .map(item -> MediaResponse.from(item, request.getContextPath()))
                .toList();
    }

    // A Resource body gets HTTP Range support from Spring for free: a Range request answers 206 with
    // just those bytes, so audio and video can be streamed and seeked. A media item's file never
    // changes (a new upload gets a new id), so the browser may cache it for good.
    @Operation(summary = "Get a media item's file",
            description = "The file itself, with its content type. Supports Range requests (206) for streaming "
                    + "and seeking. Needs the bearer token like every call.")
    @GetMapping("/{mediaId}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID orgId, @PathVariable UUID mediaId) {
        GetMediaContentQuery.MediaContent content = getMediaContentQuery.execute(orgId, mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.media().getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.media().getName(), StandardCharsets.UTF_8).build().toString())
                .body(content.file());
    }

    @Operation(summary = "Delete a media item and its file",
            description = "409 MEDIA_IN_USE while any experience still uses it.")
    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable UUID orgId, @PathVariable UUID mediaId) {
        deleteMediaUseCase.execute(orgId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
