package com.museotek.box.web.screen;

import com.museotek.box.application.screen.CancelScreenSetupCodeUseCase;
import com.museotek.box.application.screen.CreateScreenSetupCodeUseCase;
import com.museotek.box.application.screen.ListScreenSetupCodesQuery;
import com.museotek.box.application.screen.ListScreensQuery;
import com.museotek.box.application.screen.RevokeScreenUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Screens",
        description = "Museum PCs that show DISPLAY experiences. A manager adds one with a one-time setup code.")
@RestController
@RequestMapping("/api/v1/organisations/{orgId}")
public class ScreenController {

    public record CreateSetupCodeRequest(@NotBlank @Size(max = 255) String name) {
    }

    private final CreateScreenSetupCodeUseCase createSetupCodeUseCase;
    private final ListScreenSetupCodesQuery listSetupCodesQuery;
    private final CancelScreenSetupCodeUseCase cancelSetupCodeUseCase;
    private final ListScreensQuery listScreensQuery;
    private final RevokeScreenUseCase revokeScreenUseCase;

    public ScreenController(
            CreateScreenSetupCodeUseCase createSetupCodeUseCase,
            ListScreenSetupCodesQuery listSetupCodesQuery,
            CancelScreenSetupCodeUseCase cancelSetupCodeUseCase,
            ListScreensQuery listScreensQuery,
            RevokeScreenUseCase revokeScreenUseCase
    ) {
        this.createSetupCodeUseCase = createSetupCodeUseCase;
        this.listSetupCodesQuery = listSetupCodesQuery;
        this.cancelSetupCodeUseCase = cancelSetupCodeUseCase;
        this.listScreensQuery = listScreensQuery;
        this.revokeScreenUseCase = revokeScreenUseCase;
    }

    @Operation(summary = "Add screen: create a one-time setup code",
            description = "Manager only. {\"name\": \"...\"} is the name the screen will get. The response holds the "
                    + "code (e.g. K7QF-M2XR) once: it can't be read again, only cancelled. It expires after a few days.")
    @PostMapping("/screen-setup-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedSetupCodeResponse createSetupCode(@PathVariable UUID orgId,
                                                    @Valid @RequestBody CreateSetupCodeRequest request) {
        return CreatedSetupCodeResponse.from(createSetupCodeUseCase.execute(orgId, request.name()));
    }

    @Operation(summary = "List open setup codes", description = "Manager only. Not used, cancelled or expired. Without the codes.")
    @GetMapping("/screen-setup-codes")
    public List<SetupCodeResponse> listSetupCodes(@PathVariable UUID orgId) {
        return listSetupCodesQuery.execute(orgId).stream().map(SetupCodeResponse::from).toList();
    }

    @Operation(summary = "Cancel an open setup code", description = "Manager only. 404 SETUP_CODE_NOT_FOUND if it's not open.")
    @DeleteMapping("/screen-setup-codes/{setupCodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSetupCode(@PathVariable UUID orgId, @PathVariable UUID setupCodeId) {
        cancelSetupCodeUseCase.execute(orgId, setupCodeId);
    }

    @Operation(summary = "List screens", description = "By name, with when each was last seen. Removed screens are left out.")
    @GetMapping("/screens")
    public List<ScreenResponse> listScreens(@PathVariable UUID orgId) {
        return listScreensQuery.execute(orgId).stream().map(ScreenResponse::from).toList();
    }

    @Operation(summary = "Remove a screen",
            description = "Manager only. Its key stops working; a connected screen is closed within a few minutes.")
    @DeleteMapping("/screens/{screenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeScreen(@PathVariable UUID orgId, @PathVariable UUID screenId) {
        revokeScreenUseCase.execute(orgId, screenId);
    }
}
