package com.museotek.box.web.box;

import com.museotek.box.application.box.CreateBoxKeyUseCase;
import com.museotek.box.application.box.CreateBoxKeyUseCase.CreatedBoxKey;
import com.museotek.box.application.box.RemoveBoxKeyUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A Box's login key, managers only. POST makes one (replacing the old one, which stops working at once) and returns
 * it this one time; DELETE removes it. The Box page shows only whether a key exists and when it was made.
 */
@RestController
@RequestMapping("/api/v1/organisations/{orgId}/boxes/{boxId}/key")
public class BoxKeyController {

    private final CreateBoxKeyUseCase createUseCase;
    private final RemoveBoxKeyUseCase removeUseCase;

    public BoxKeyController(CreateBoxKeyUseCase createUseCase, RemoveBoxKeyUseCase removeUseCase) {
        this.createUseCase = createUseCase;
        this.removeUseCase = removeUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedBoxKey create(@PathVariable UUID orgId, @PathVariable Long boxId) {
        return createUseCase.execute(orgId, boxId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID orgId, @PathVariable Long boxId) {
        removeUseCase.execute(orgId, boxId);
    }
}
