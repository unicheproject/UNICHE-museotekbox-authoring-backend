package com.museotek.box.domain.experience;

/** Where an experience plays, chosen once when it is created ("Where does it play?"). */
public enum ExperienceOutput {
    /** Box and a screen: questions are shown, replies appear as a coloured overlay. */
    DISPLAY,
    /** Box only: no screen, everything is sound from the Box. */
    BOX
}
