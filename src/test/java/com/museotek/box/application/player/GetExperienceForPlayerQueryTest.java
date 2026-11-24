package com.museotek.box.application.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.publish.PublishedContentMapper;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.ExperienceNotPublishedException;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetExperienceForPlayerQueryTest {

    private static final Instant PUBLISHED = Instant.parse("2026-11-03T10:00:00Z");

    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final PublishedExperienceRepository publishedExperienceRepository = mock(PublishedExperienceRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final GetExperienceForPlayerQuery query = new GetExperienceForPlayerQuery(
            boxRepository, projectRepository, publishedExperienceRepository, mediaRepository,
            new PublishedContentMapper(new ObjectMapper()));

    private final UUID projectId = UUID.randomUUID();
    private final Project project = new Project();

    @BeforeEach
    void anAssignedLiveExperience() {
        project.setId(projectId);
        when(boxRepository.existsById(1L)).thenReturn(true);
        when(boxRepository.findAssignedProjectIds(1L)).thenReturn(List.of(projectId));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    }

    @Test
    void theLatestCopy_comesWithTheMediaTheBoxPlays() {
        UUID sound = UUID.randomUUID();
        Media media = media(sound, MediaKind.AUDIO, "audio/mpeg");
        String content = "{\"scenes\":[{\"blocks\":[{\"type\":\"AUDIO\",\"content\":{\"mediaId\":\"" + sound
                + "\"}}],\"rules\":[]}]}";
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.of(
                new PublishedExperience(project, 4, "Wing", ExperienceOutput.DISPLAY, 7L, content,
                        List.of(sound.toString()), List.of(7L), PUBLISHED, "someone")));
        when(mediaRepository.findAllById(List.of(sound))).thenReturn(List.of(media));

        PlayerExperienceCopy copy = query.execute(1L, projectId);

        assertThat(copy.experience()).isEqualTo(new PlayerExperience(projectId, "Wing", 4, ExperienceOutput.DISPLAY, 7L));
        assertThat(copy.publishedAt()).isEqualTo(PUBLISHED);
        assertThat(copy.content()).isEqualTo(content);
        assertThat(copy.media()).containsExactly(
                new PlayerExperienceCopy.MediaFile(sound, MediaKind.AUDIO, "audio/mpeg", 1234L, "ab12"));
    }

    @Test
    void displayPicturesAndVideos_areLeftOutOfTheList() {
        UUID video = UUID.randomUUID();
        UUID picture = UUID.randomUUID();
        UUID boxVideo = UUID.randomUUID();
        UUID replySound = UUID.randomUUID();
        String content = "{\"scenes\":[{\"blocks\":["
                + "{\"type\":\"VIDEO\",\"content\":{\"mediaId\":\"" + video + "\"}},"
                + "{\"type\":\"BOX_VIDEO\",\"content\":{\"mediaId\":\"" + boxVideo + "\"}}],"
                + "\"rules\":[{\"effects\":[{\"type\":\"REPLY\",\"media\":\"" + picture
                + "\",\"audio\":\"" + replySound + "\"}]}]}]}";
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.of(
                new PublishedExperience(project, 4, "Wing", ExperienceOutput.DISPLAY, 7L, content,
                        List.of(video.toString(), picture.toString(), boxVideo.toString(), replySound.toString()),
                        List.of(7L), PUBLISHED, "someone")));
        when(mediaRepository.findAllById(List.of(boxVideo, replySound))).thenReturn(List.of(
                media(boxVideo, MediaKind.VIDEO, "video/mp4"), media(replySound, MediaKind.AUDIO, "audio/mpeg")));

        PlayerExperienceCopy copy = query.execute(1L, projectId);

        assertThat(copy.media()).extracting(PlayerExperienceCopy.MediaFile::id).containsExactly(boxVideo, replySound);
    }

    @Test
    void anUnknownBox_throws() {
        when(boxRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> query.execute(1L, projectId)).isInstanceOf(BoxNotFoundException.class);
    }

    @Test
    void anExperienceNotOnThatBox_isNotFound() {
        when(boxRepository.findAssignedProjectIds(1L)).thenReturn(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> query.execute(1L, projectId)).isInstanceOf(ExperienceNotPublishedException.class);
    }

    @Test
    void aDeletedExperience_isNotFound() {
        project.setDeletedAt(PUBLISHED);

        assertThatThrownBy(() -> query.execute(1L, projectId)).isInstanceOf(ExperienceNotPublishedException.class);
    }

    @Test
    void anExperienceWithoutACopy_isNotFound() {
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(1L, projectId)).isInstanceOf(ExperienceNotPublishedException.class);
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
}
