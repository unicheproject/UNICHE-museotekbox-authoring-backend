package com.museotek.box.application.box;

import com.museotek.box.domain.box.StartCardInUseException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * A visitor picks which of a Box's experiences to play by placing its start card, so two
 * experiences on the same Box can't share one. Checked from both sides: when an experience is
 * assigned to a Box, and when an assigned experience is saved with a different start card.
 * Experiences on different Boxes may share a start card. Deleted experiences don't count.
 */
@Service
public class BoxStartCards {

    private final BoxRepository boxRepository;

    public BoxStartCards(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;
    }

    public void requireFreeOnBox(Long boxId, UUID projectId, Long startCardTypeId) {
        List<String> clashing = boxRepository.findProjectNamesWithStartCard(boxId, projectId, startCardTypeId);
        if (!clashing.isEmpty()) {
            throw new StartCardInUseException("Another experience on this box already starts with the same card: "
                    + String.join(", ", clashing));
        }
    }

    public void requireFreeOnAssignedBoxes(UUID projectId, Long startCardTypeId) {
        List<String> boxes = boxRepository.findBoxNamesWhereStartCardIsTaken(projectId, startCardTypeId);
        if (!boxes.isEmpty()) {
            throw new StartCardInUseException("Another experience already starts with the same card on box "
                    + String.join(", ", boxes));
        }
    }
}
