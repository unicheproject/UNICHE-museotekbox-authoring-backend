package com.museotek.box.domain.block;

/**
 * What a block puts on the screen or through the speaker. Media blocks point to a media library
 * item by {@code content.mediaId}.
 */
public enum BlockType {
    /** Words on the screen. */
    TEXT,
    /** A picture on the screen. */
    IMAGE,
    /** A drawing on the screen: a picture, used as an illustration. */
    ILLUSTRATION,
    /** A video on the screen. */
    VIDEO,
    /** A ready-made question screen: the question and up to four answers, optionally a picture or video. */
    SCREEN,
    /** A sound from the Box. */
    AUDIO,
    /** A video on the Box's own display. */
    BOX_VIDEO
}
