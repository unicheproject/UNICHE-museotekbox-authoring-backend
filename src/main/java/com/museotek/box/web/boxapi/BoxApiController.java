package com.museotek.box.web.boxapi;

import com.museotek.box.application.media.GetMediaContentQuery.MediaContent;
import com.museotek.box.application.player.GetBoxForPlayerQuery;
import com.museotek.box.application.player.GetMediaOnBoxQuery;
import com.museotek.box.application.player.PlayerBoxIdentity;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * {@code /box}: the Box's REST API, called with its Box key. The key decides the Box: nothing here takes a Box or
 * organisation id, so a Box only ever reads its own experiences and media.
 */
@RestController
@RequestMapping("/box")
public class BoxApiController {

    private final GetBoxForPlayerQuery boxQuery;
    private final GetMediaOnBoxQuery mediaQuery;

    public BoxApiController(GetBoxForPlayerQuery boxQuery, GetMediaOnBoxQuery mediaQuery) {
        this.boxQuery = boxQuery;
        this.mediaQuery = mediaQuery;
    }

    // Everything the Box runs, files apart: called at every start, reconnect and sync.required.
    @GetMapping("/sync")
    public BoxSyncResponse sync(@AuthenticationPrincipal PlayerBoxIdentity box) {
        return BoxSyncResponse.from(boxQuery.execute(box.boxId()));
    }

    // 404 MEDIA_NOT_FOUND for media no experience on this Box uses. Range support from the Resource body, and
    // cacheable for good since a media item's file never changes (a new upload gets a new id).
    @GetMapping("/media/{mediaId}/content")
    public ResponseEntity<Resource> mediaContent(@AuthenticationPrincipal PlayerBoxIdentity box,
                                                 @PathVariable UUID mediaId) {
        MediaContent content = mediaQuery.execute(box.boxId(), mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.media().getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.media().getName(), StandardCharsets.UTF_8).build().toString())
                .body(content.file());
    }
}
