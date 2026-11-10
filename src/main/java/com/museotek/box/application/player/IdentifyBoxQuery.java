package com.museotek.box.application.player;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

/** Which Box a connecting device is, by its serial number. The serial is kept private, so it is never echoed back. */
@Service
public class IdentifyBoxQuery {

    private final BoxRepository boxRepository;

    public IdentifyBoxQuery(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;
    }

    public Box execute(String serialNumber) {
        return boxRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new BoxNotFoundException("No Box with that serial number"));
    }
}
