package com.museotek.box.application.player;

/** Which card type an RFID tag stands for. */
public record PlayerScanObject(String rfidTag, Long cardTypeId) {
}
