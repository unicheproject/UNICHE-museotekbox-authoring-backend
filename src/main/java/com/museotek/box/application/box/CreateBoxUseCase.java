package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.DuplicateSerialNumberException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateBoxUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;

    public CreateBoxUseCase(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
    }

    public Box execute(UUID orgId, String name, String serialNumber) {
        orgAccessGuard.requireAccess(orgId);

        if (boxRepository.findBySerialNumber(serialNumber).isPresent()) {
            throw new DuplicateSerialNumberException("A box with serial number " + serialNumber + " already exists");
        }

        Box box = new Box();
        box.setOrgId(orgId);
        box.setName(name);
        box.setSerialNumber(serialNumber);
        return boxRepository.save(box);
    }
}
