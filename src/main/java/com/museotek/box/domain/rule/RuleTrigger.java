package com.museotek.box.domain.rule;

/** When a rule fires. */
public enum RuleTrigger {
    /** This card type is placed on the Box (the rule's scan object type). */
    SCAN,
    /** Any card not matched by an earlier rule of the scene is placed: the catch-all. */
    SCAN_OTHER,
    /** The rule's seconds have passed in this scene. */
    TIMER_ELAPSED,
    /** The scene's video finished. */
    VIDEO_ENDED,
    /** The visitor arrives in this scene. */
    SCENE_ENTERED,
    /** The experience starts. Start scene only. */
    SESSION_STARTED
}
