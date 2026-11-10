package com.museotek.box.application.player;

import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.media.Media;
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
            boxRepository, projectRepository, publishedExperienceRepository, mediaRepository);

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
    void theLatestCopy_comesWithItsMedia() {
        UUID mediaId = UUID.randomUUID();
        Media media = new Media();
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.of(
                new PublishedExperience(project, 4, "Wing", ExperienceOutput.DISPLAY, 7L, "{\"scenes\":[]}",
                        List.of(mediaId.toString()), List.of(7L), PUBLISHED, "someone")));
        when(mediaRepository.findAllById(List.of(mediaId))).thenReturn(List.of(media));

        PlayerExperienceCopy copy = query.execute(1L, projectId);

        assertThat(copy.experience()).isEqualTo(new PlayerExperience(projectId, "Wing", 4, ExperienceOutput.DISPLAY, 7L));
        assertThat(copy.publishedAt()).isEqualTo(PUBLISHED);
        assertThat(copy.content()).isEqualTo("{\"scenes\":[]}");
        assertThat(copy.media()).containsExactly(media);
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
}
