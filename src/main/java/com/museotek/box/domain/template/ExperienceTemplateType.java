package com.museotek.box.domain.template;

/**
 * The kinds of activity the wizard can build. Fixed in code on purpose: each type needs its own
 * form and compiler in the frontend, so a new type can't be created through the API. Declared in
 * the order the wizard lists them.
 */
public enum ExperienceTemplateType {
    SCAN_AND_DISCOVER,
    QUIZ,
    TREASURE_HUNT,
    GUIDED_PRESENTATION,
    FROM_SCRATCH
}
