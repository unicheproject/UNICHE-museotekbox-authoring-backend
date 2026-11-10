package com.museotek.box.web.player;

import com.museotek.box.application.media.GetMediaContentQuery.MediaContent;
import com.museotek.box.application.player.GetBoxForPlayerQuery;
import com.museotek.box.application.player.GetExperienceForPlayerQuery;
import com.museotek.box.application.player.GetMediaForPlayerQuery;
import com.museotek.box.application.player.IdentifyBoxQuery;
import com.museotek.box.application.player.ListOrgBoxesForPlayerQuery;
import com.museotek.box.application.player.ReportBoxStatusUseCase;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** {@code /player/v1}: what the player project needs to run Boxes, and what it reports back. Only the player project calls it. */
@RestController
@RequestMapping("/player/v1")
public class PlayerController {

    private final IdentifyBoxQuery identifyBoxQuery;
    private final ListOrgBoxesForPlayerQuery listOrgBoxesQuery;
    private final GetBoxForPlayerQuery getBoxQuery;
    private final GetExperienceForPlayerQuery getExperienceQuery;
    private final ReportBoxStatusUseCase reportBoxStatusUseCase;
    private final GetMediaForPlayerQuery getMediaQuery;
    private final int pollSeconds;

    public PlayerController(
            IdentifyBoxQuery identifyBoxQuery,
            ListOrgBoxesForPlayerQuery listOrgBoxesQuery,
            GetBoxForPlayerQuery getBoxQuery,
            GetExperienceForPlayerQuery getExperienceQuery,
            ReportBoxStatusUseCase reportBoxStatusUseCase,
            GetMediaForPlayerQuery getMediaQuery,
            @Value("${museotek.player.poll-seconds}") int pollSeconds
    ) {
        this.identifyBoxQuery = identifyBoxQuery;
        this.listOrgBoxesQuery = listOrgBoxesQuery;
        this.getBoxQuery = getBoxQuery;
        this.getExperienceQuery = getExperienceQuery;
        this.reportBoxStatusUseCase = reportBoxStatusUseCase;
        this.getMediaQuery = getMediaQuery;
        this.pollSeconds = pollSeconds;
    }

    @PostMapping("/boxes/identify")
    public PlayerBoxIdentityResponse identifyBox(@Valid @RequestBody IdentifyBoxRequest request) {
        return PlayerBoxIdentityResponse.from(identifyBoxQuery.execute(request.serialNumber()));
    }

    @GetMapping("/organisations/{orgId}/boxes")
    public List<PlayerBoxSummaryResponse> listOrgBoxes(@PathVariable UUID orgId) {
        return listOrgBoxesQuery.execute(orgId).stream().map(PlayerBoxSummaryResponse::from).toList();
    }

    @GetMapping("/boxes/{boxId}")
    public PlayerBoxResponse getBox(@PathVariable Long boxId) {
        return PlayerBoxResponse.from(getBoxQuery.execute(boxId), pollSeconds);
    }

    @GetMapping("/boxes/{boxId}/experiences/{projectId}")
    public PlayerExperienceResponse getExperience(@PathVariable Long boxId, @PathVariable UUID projectId) {
        return PlayerExperienceResponse.from(getExperienceQuery.execute(boxId, projectId));
    }

    // Same as the editor's download: Range support from the Resource body, and cacheable for good since a media
    // item's file never changes (a new upload gets a new id).
    @GetMapping("/media/{mediaId}/content")
    public ResponseEntity<Resource> mediaContent(@PathVariable UUID mediaId) {
        MediaContent content = getMediaQuery.execute(mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.media().getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.media().getName(), StandardCharsets.UTF_8).build().toString())
                .body(content.file());
    }

    @PutMapping("/boxes/{boxId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportStatus(@PathVariable Long boxId, @Valid @RequestBody BoxStatusRequest request) {
        reportBoxStatusUseCase.execute(boxId, request.toReport());
    }
}
