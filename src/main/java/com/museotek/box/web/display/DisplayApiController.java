package com.museotek.box.web.display;

import com.museotek.box.application.media.GetMediaContentQuery.MediaContent;
import com.museotek.box.application.player.DisplayMediaLinks.SignedLink;
import com.museotek.box.application.player.GetDisplayIdleQuery;
import com.museotek.box.application.player.GetDisplayIdleQuery.DisplayIdleView;
import com.museotek.box.application.player.GetDisplayMediaQuery;
import com.museotek.box.application.player.GetDisplayMediaQuery.DisplayMedia;
import com.museotek.box.application.player.PlayerDisplayView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * {@code /display}: the display page's REST API, called with its display token (media with a signed link instead).
 * The token decides the Box: nothing here takes a Box or organisation id.
 */
@RestController
@RequestMapping("/display")
public class DisplayApiController {

    private final GetDisplayIdleQuery idleQuery;
    private final GetDisplayMediaQuery mediaQuery;
    private final Clock clock;

    public DisplayApiController(GetDisplayIdleQuery idleQuery, GetDisplayMediaQuery mediaQuery, Clock clock) {
        this.idleQuery = idleQuery;
        this.mediaQuery = mediaQuery;
        this.clock = clock;
    }

    @GetMapping("/idle")
    public DisplayIdleView idle(@AuthenticationPrincipal PlayerDisplayView display) {
        return idleQuery.execute(display);
    }

    // 403 MEDIA_LINK_INVALID for a wrong or expired link or a revoked display; 404 MEDIA_NOT_FOUND for media that
    // isn't on the display's Box. Range requests work through the Resource body. Cached privately until the link
    // expires, no longer.
    @GetMapping("/media/{mediaId}/content")
    public ResponseEntity<Resource> mediaContent(@PathVariable UUID mediaId, @RequestParam UUID session,
                                                 @RequestParam long expires, @RequestParam String signature) {
        DisplayMedia media = mediaQuery.execute(new SignedLink(mediaId, session, expires, signature));
        MediaContent content = media.content();
        Duration untilExpiry = Duration.between(clock.instant(), media.linkExpiresAt());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.media().getContentType()))
                .cacheControl(CacheControl.maxAge(untilExpiry).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.media().getName(), StandardCharsets.UTF_8).build().toString())
                .body(content.file());
    }
}
