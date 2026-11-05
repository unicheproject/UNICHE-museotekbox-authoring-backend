package com.museotek.box.application.box;

import com.museotek.box.domain.box.StartCardInUseException;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoxStartCardsTest {

    private final PublishedExperienceRepository publishedExperienceRepository = mock(PublishedExperienceRepository.class);
    private final BoxStartCards boxStartCards = new BoxStartCards(publishedExperienceRepository);

    private final UUID projectId = UUID.randomUUID();

    @Test
    void freeOnBox_passes() {
        when(publishedExperienceRepository.findProjectNamesOnBoxWithStartCard(1L, projectId, 7L)).thenReturn(List.of());

        assertThatCode(() -> boxStartCards.requireFreeOnBox(1L, projectId, 7L)).doesNotThrowAnyException();
    }

    @Test
    void takenOnBox_namesTheOtherExperience() {
        when(publishedExperienceRepository.findProjectNamesOnBoxWithStartCard(1L, projectId, 7L)).thenReturn(List.of("Treasure hunt"));

        assertThatThrownBy(() -> boxStartCards.requireFreeOnBox(1L, projectId, 7L))
                .isInstanceOf(StartCardInUseException.class)
                .hasMessageContaining("Treasure hunt");
    }

    @Test
    void takenOnAnAssignedBox_namesTheBox() {
        when(publishedExperienceRepository.findBoxNamesWhereStartCardIsTaken(projectId, 7L)).thenReturn(List.of("Hall A"));

        assertThatThrownBy(() -> boxStartCards.requireFreeOnAssignedBoxes(projectId, 7L))
                .isInstanceOf(StartCardInUseException.class)
                .hasMessageContaining("Hall A");
    }
}
