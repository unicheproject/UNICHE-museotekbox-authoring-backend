package com.museotek.box.application.player;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.domain.scanobject.ColouredCard;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetBoxForPlayerQueryTest {

    private final BoxRepository boxRepository = mock(BoxRepository.class);
    private final PublishedExperienceRepository publishedExperienceRepository = mock(PublishedExperienceRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final GetBoxForPlayerQuery query = new GetBoxForPlayerQuery(
            boxRepository, publishedExperienceRepository, scanObjectTypeRepository, scanObjectRepository);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void anUnknownBox_throws() {
        when(boxRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(1L)).isInstanceOf(BoxNotFoundException.class);
    }

    @Test
    void theBox_comesWithItsLatestCopies_andTheOrgsCardTypes() {
        Box box = box();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        PublishedExperience copy = new PublishedExperience(project, 3, "Wing", ExperienceOutput.BOX, 7L, "{}",
                List.of(), List.of(7L), Instant.parse("2026-11-03T10:00:00Z"), "someone");
        ScanObjectType red = cardType(7L);
        when(boxRepository.findById(1L)).thenReturn(Optional.of(box));
        when(publishedExperienceRepository.findLatestCopiesOnBox(1L)).thenReturn(List.of(copy));
        when(scanObjectTypeRepository.findByOrgId(orgId)).thenReturn(List.of(red));
        when(scanObjectRepository.findByOrgId(orgId)).thenReturn(List.of());

        PlayerBoxView view = query.execute(1L);

        assertThat(view.box()).isEqualTo(new PlayerBoxView.BoxSummary(1L, orgId, "Hall A"));
        assertThat(view.experiences()).containsExactly(
                new PlayerExperience(project.getId(), "Wing", 3, ExperienceOutput.BOX, 7L));
        assertThat(view.cardTypes()).containsExactly(new PlayerBoxView.CardType(7L, red.getName(), false, false));
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
