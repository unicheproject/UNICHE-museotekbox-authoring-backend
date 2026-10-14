package com.museotek.box.application.box;

import com.museotek.box.application.experience.ExperienceGaps;
import com.museotek.box.domain.box.ExperienceNotReadyException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublishedExperienceCheckTest {

    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final ExperienceGaps experienceGaps = mock(ExperienceGaps.class);
    private final PublishedExperienceCheck check = new PublishedExperienceCheck(boxRepository, experienceGaps);

    private final UUID projectId = UUID.randomUUID();

    @Test
    void notPublished_gapsAreAllowed() {
        when(boxRepository.findNamesByAssignedProjectId(projectId)).thenReturn(List.of());

        assertThatCode(() -> check.requireNoGapsIfPublished(projectId)).doesNotThrowAnyException();
        verifyNoInteractions(experienceGaps);
    }

    @Test
    void publishedWithoutGaps_passes() {
        when(boxRepository.findNamesByAssignedProjectId(projectId)).thenReturn(List.of("Hall A"));
        when(experienceGaps.find(projectId)).thenReturn(List.of());

        assertThatCode(() -> check.requireNoGapsIfPublished(projectId)).doesNotThrowAnyException();
    }

    @Test
    void publishedWithGaps_isRefusedWithTheGapsAndTheBoxes() {
        when(boxRepository.findNamesByAssignedProjectId(projectId)).thenReturn(List.of("Hall A", "Hall B"));
        when(experienceGaps.find(projectId)).thenReturn(List.of("block '4' has no file chosen"));

        assertThatThrownBy(() -> check.requireNoGapsIfPublished(projectId))
                .isInstanceOf(ExperienceNotReadyException.class)
                .hasMessageContaining("Hall A, Hall B")
                .hasMessageContaining("unpublish")
                .extracting(e -> ((ExperienceNotReadyException) e).getGaps())
                .isEqualTo(List.of("block '4' has no file chosen"));
    }
}
