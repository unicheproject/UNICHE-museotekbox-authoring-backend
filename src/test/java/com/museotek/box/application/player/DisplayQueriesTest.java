package com.museotek.box.application.player;

import com.museotek.box.application.player.DisplayMediaLinks.SignedLink;
import com.museotek.box.application.player.GetDisplayIdleQuery.DisplayIdleView;
import com.museotek.box.application.player.GetDisplayIdleQuery.IdleExperience;
import com.museotek.box.application.player.GetDisplayIdleQuery.StartCard;
import com.museotek.box.application.player.GetDisplayMediaQuery.DisplayMedia;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.domain.display.InvalidMediaLinkException;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaNotFoundException;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import com.museotek.box.infrastructure.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// GET /display/idle and the signed media links, against mocked repositories.
class DisplayQueriesTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final PublishedExperienceRepository publishedRepository = mock(PublishedExperienceRepository.class);
    private final ScanObjectTypeRepository cardTypeRepository = mock(ScanObjectTypeRepository.class);
    private final DisplaySessionRepository sessionRepository = mock(DisplaySessionRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final MediaStorage mediaStorage = mock(MediaStorage.class);
    private final DisplayMediaLinks links = mock(DisplayMediaLinks.class);

    private final GetDisplayIdleQuery idle =
            new GetDisplayIdleQuery(boxRepository, publishedRepository, cardTypeRepository);
    private final GetDisplayMediaQuery media = new GetDisplayMediaQuery(links, sessionRepository,
            new GetMediaOnBoxQuery(publishedRepository, mediaRepository, mediaStorage), CLOCK);

    private final UUID orgId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();
    private final SignedLink link = new SignedLink(mediaId, sessionId, NOW.plusSeconds(600).getEpochSecond(), "sig");

    @Test
    void idle_listsTheBoxsExperiencesWithTheirStartCards_andTheStoredOrgName() {
        Box box = new Box();
        box.setId(7L);
        box.setOrgId(orgId);
        box.setName("Hall A");
        box.setStatus(BoxStatus.ONLINE);
        when(boxRepository.findById(7L)).thenReturn(Optional.of(box));
        ScanObjectType muses = new ScanObjectType();
        muses.setId(3L);
        muses.setName("Muses");
        when(cardTypeRepository.findByOrgId(orgId)).thenReturn(List.of(muses));
        Project project = new Project();
        project.setId(UUID.randomUUID());
        when(publishedRepository.findLatestCopiesOnBox(7L)).thenReturn(List.of(new PublishedExperience(project, 2,
                "Art out of the box", ExperienceOutput.DISPLAY, 3L, "{}", List.of(), List.of(3L), NOW, "someone")));

        DisplayIdleView view = idle.execute(new PlayerDisplayView(sessionId, 7L, orgId, "Museum of Art"));

        assertThat(view.orgName()).isEqualTo("Museum of Art");
        assertThat(view.boxId()).isEqualTo(7L);
        assertThat(view.boxName()).isEqualTo("Hall A");
        assertThat(view.boxStatus()).isEqualTo(BoxStatus.ONLINE);
        assertThat(view.experiences()).containsExactly(new IdleExperience(project.getId(), "Art out of the box",
                ExperienceOutput.DISPLAY, new StartCard(3L, "Muses")));
    }

    @Test
    void media_aLinkTheToolDidNotSign_isRefusedBeforeAnyLookup() {
        when(links.isValid(link)).thenReturn(false);

        assertThatThrownBy(() -> media.execute(link)).isInstanceOf(InvalidMediaLinkException.class);
        verify(sessionRepository, never()).findById(sessionId);
    }

    @Test
    void media_ofARevokedOrExpiredDisplay_isRefused() {
        when(links.isValid(link)).thenReturn(true);
        DisplaySession revoked = session(NOW.plus(Duration.ofDays(1)));
        revoked.setRevokedAt(NOW.minusSeconds(1));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> media.execute(link)).isInstanceOf(InvalidMediaLinkException.class);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session(NOW)));
        assertThatThrownBy(() -> media.execute(link)).isInstanceOf(InvalidMediaLinkException.class);
    }

    @Test
    void media_notOnTheDisplaysBox_is404() {
        when(links.isValid(link)).thenReturn(true);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session(NOW.plus(Duration.ofDays(1)))));
        when(publishedRepository.latestCopyOnBoxUsesMedia(7L, mediaId.toString())).thenReturn(false);

        assertThatThrownBy(() -> media.execute(link)).isInstanceOf(MediaNotFoundException.class);
        verify(mediaRepository, never()).findById(mediaId);
    }

    @Test
    void media_onTheBox_returnsTheFile_andWhenTheLinkExpires() {
        when(links.isValid(link)).thenReturn(true);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session(NOW.plus(Duration.ofDays(1)))));
        when(publishedRepository.latestCopyOnBoxUsesMedia(7L, mediaId.toString())).thenReturn(true);
        Media file = new Media();
        file.setId(mediaId);
        file.setStoredFilename("stored.mp4");
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(file));
        ByteArrayResource bytes = new ByteArrayResource(new byte[]{1, 2, 3});
        when(mediaStorage.load("stored.mp4")).thenReturn(bytes);

        DisplayMedia result = media.execute(link);

        assertThat(result.content().file()).isSameAs(bytes);
        assertThat(result.linkExpiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    private DisplaySession session(Instant expiresAt) {
        DisplaySession session = new DisplaySession();
        session.setId(sessionId);
        session.setBoxId(7L);
        session.setExpiresAt(expiresAt);
        return session;
    }
}
