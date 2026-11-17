package com.museotek.box.application.player;

import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.InvalidBoxKeyException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

/** Checks a Box's key when it calls the Box API (and, later, when its socket logs in). */
@Service
public class VerifyBoxKeyUseCase {

    private final BoxRepository boxRepository;

    public VerifyBoxKeyUseCase(BoxRepository boxRepository) {
        this.boxRepository = boxRepository;
    }

    public PlayerBoxIdentity execute(String key) {
        Box box = boxRepository.findByKeyHash(SecretTokens.sha256Hex(key))
                .orElseThrow(() -> new InvalidBoxKeyException("Unknown, replaced or removed box key"));
        return new PlayerBoxIdentity(box.getId(), box.getOrgId());
    }
}
