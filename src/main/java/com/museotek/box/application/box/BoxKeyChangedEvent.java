package com.museotek.box.application.box;

/**
 * A Box's key was replaced or removed: the old key stops working. Published inside that transaction; listeners act
 * after it commits (the Box's socket, once it has one, is closed).
 */
public record BoxKeyChangedEvent(Long boxId) {
}
