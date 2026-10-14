package com.museotek.box.domain.box;

import java.util.List;

/** An experience with gaps can't be assigned to a Box. Maps to 422 in GlobalExceptionHandler, with the gaps as details. */
public class ExperienceNotReadyException extends RuntimeException {

    private final List<String> gaps;

    public ExperienceNotReadyException(String message, List<String> gaps) {
        super(message);
        this.gaps = List.copyOf(gaps);
    }

    public List<String> getGaps() {
        return gaps;
    }
}
