package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.box.DuplicateSerialNumberException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UpdateBoxUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;

    public UpdateBoxUseCase(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
    }

    public Box execute(UUID orgId, Long boxId, String name, String serialNumber) {
        orgAccessGuard.requireManager(orgId);

        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));

        boxRepository.findBySerialNumber(serialNumber)
                .filter(other -> !other.getId().equals(boxId))
                .ifPresent(other -> {
                    throw new DuplicateSerialNumberException("A box with serial number " + serialNumber + " already exists");
                });

        box.setName(name);
        box.setSerialNumber(serialNumber);
        return boxRepository.save(box);
    }
}
