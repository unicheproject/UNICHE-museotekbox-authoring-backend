package com.museotek.box.application.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.publish.PublishedContentMapper;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetBoxForPlayerQueryTest {

    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final PublishedExperienceRepository publishedExperienceRepository = mock(PublishedExperienceRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final GetBoxForPlayerQuery query = new GetBoxForPlayerQuery(
            boxRepository, publishedExperienceRepository, scanObjectTypeRepository, scanObjectRepository,
            mediaRepository, new PublishedContentMapper(new ObjectMapper()));

    private static final Instant PUBLISHED = Instant.parse("2026-11-03T10:00:00Z");

    private final UUID orgId = UUID.randomUUID();

    @Test
    void anUnknownBox_throws() {
        when(boxRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(1L)).isInstanceOf(BoxNotFoundException.class);
    }

    @Test
    void theBox_comesWithItsLatestCopies_andTheOrgsCardTypes() {
        Box box = box();
        Project project = project();
        PublishedExperience copy = new PublishedExperience(project, 3, "Wing", ExperienceOutput.BOX, 7L, "{}",
                List.of(), List.of(7L), PUBLISHED, "someone");
        ScanObjectType red = cardType(7L);
        when(boxRepository.findById(1L)).thenReturn(Optional.of(box));
        when(publishedExperienceRepository.findLatestCopiesOnBox(1L)).thenReturn(List.of(copy));
        when(scanObjectTypeRepository.findByOrgId(orgId)).thenReturn(List.of(red));
        when(scanObjectRepository.findByOrgId(orgId)).thenReturn(List.of());

        PlayerBoxView view = query.execute(1L);

        assertThat(view.box()).isEqualTo(new PlayerBoxView.BoxSummary(1L, orgId, "Hall A"));
        assertThat(view.experiences()).containsExactly(new PlayerExperienceCopy(
                new PlayerExperience(project.getId(), "Wing", 3, ExperienceOutput.BOX, 7L), PUBLISHED, "{}", List.of()));
        assertThat(view.cardTypes()).containsExactly(new PlayerBoxView.CardType(7L, red.getName(), false, false));
    }

    @Test
    void eachCopy_listsOnlyTheMediaTheBoxPlays() {
        UUID video = UUID.randomUUID();
        UUID picture = UUID.randomUUID();
        UUID boxVideo = UUID.randomUUID();
        UUID replySound = UUID.randomUUID();
        String content = "{\"scenes\":[{\"blocks\":["
                + "{\"type\":\"VIDEO\",\"content\":{\"mediaId\":\"" + video + "\"}},"
                + "{\"type\":\"BOX_VIDEO\",\"content\":{\"mediaId\":\"" + boxVideo + "\"}}],"
                + "\"rules\":[{\"effects\":[{\"type\":\"REPLY\",\"media\":\"" + picture
                + "\",\"audio\":\"" + replySound + "\"}]}]}]}";
        PublishedExperience copy = new PublishedExperience(project(), 4, "Wing", ExperienceOutput.DISPLAY, 7L, content,
                List.of(video.toString(), picture.toString(), boxVideo.toString(), replySound.toString()),
                List.of(7L), PUBLISHED, "someone");
        when(boxRepository.findById(1L)).thenReturn(Optional.of(box()));
        when(publishedExperienceRepository.findLatestCopiesOnBox(1L)).thenReturn(List.of(copy));
        when(mediaRepository.findAllById(new LinkedHashSet<>(List.of(boxVideo, replySound)))).thenReturn(List.of(
                media(replySound, MediaKind.AUDIO, "audio/mpeg"), media(boxVideo, MediaKind.VIDEO, "video/mp4")));

        PlayerExperienceCopy experience = query.execute(1L).experiences().get(0);

        assertThat(experience.content()).isEqualTo(content);
        assertThat(experience.media()).containsExactly(
                new PlayerExperienceCopy.MediaFile(boxVideo, MediaKind.VIDEO, "video/mp4", 1234L, "ab12"),
                new PlayerExperienceCopy.MediaFile(replySound, MediaKind.AUDIO, "audio/mpeg", 1234L, "ab12"));
    }

    @Test
    void aFileUsedByTwoCopies_isLookedUpOnce_andListedInBoth() {
        UUID sound = UUID.randomUUID();
        String content = "{\"scenes\":[{\"blocks\":[{\"type\":\"AUDIO\",\"content\":{\"mediaId\":\"" + sound
                + "\"}}],\"rules\":[]}]}";
        PublishedExperience wing = new PublishedExperience(project(), 1, "Wing", ExperienceOutput.BOX, 7L, content,
                List.of(sound.toString()), List.of(7L), PUBLISHED, "someone");
        PublishedExperience hall = new PublishedExperience(project(), 2, "Hall", ExperienceOutput.BOX, 8L, content,
                List.of(sound.toString()), List.of(8L), PUBLISHED, "someone");
        when(boxRepository.findById(1L)).thenReturn(Optional.of(box()));
        when(publishedExperienceRepository.findLatestCopiesOnBox(1L)).thenReturn(List.of(wing, hall));
        when(mediaRepository.findAllById(any())).thenReturn(List.of(media(sound, MediaKind.AUDIO, "audio/mpeg")));

        List<PlayerExperienceCopy> experiences = query.execute(1L).experiences();

        assertThat(experiences).allSatisfy(experience -> assertThat(experience.media())
                .extracting(PlayerExperienceCopy.MediaFile::id).containsExactly(sound));
    }

    @Test
    void cardsWithoutATagOrAType_areLeftOut() {
        ScanObjectType red = cardType(7L);
        when(boxRepository.findById(1L)).thenReturn(Optional.of(box()));
        when(scanObjectRepository.findByOrgId(orgId)).thenReturn(List.of(
                card("04A1", red), card(null, red), card("04B2", null)));

        assertThat(query.execute(1L).scanObjects()).containsExactly(new PlayerScanObject("04A1", 7L));
    }

    private Box box() {
        Box box = new Box();
        box.setId(1L);
        box.setOrgId(orgId);
        box.setName("Hall A");
        return box;
    }

    private static Project project() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        return project;
    }

    private static Media media(UUID id, MediaKind kind, String contentType) {
        Media media = new Media();
        media.setId(id);
        media.setKind(kind);
        media.setContentType(contentType);
        media.setSizeBytes(1234L);
        media.setSha256("ab12");
        return media;
    }

    private static ScanObjectType cardType(Long id) {
        ScanObjectType type = new ScanObjectType();
        type.setId(id);
        return type;
    }

    private static ColouredCard card(String rfidTag, ScanObjectType type) {
        ColouredCard card = new ColouredCard();
        card.setRfidTag(rfidTag);
        card.setScanObjectType(type);
        return card;
    }
}
