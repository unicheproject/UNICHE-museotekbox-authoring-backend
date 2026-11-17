package com.museotek.box.web.display;

import com.museotek.box.application.display.CreateDisplaySessionUseCase;
import com.museotek.box.application.display.ListDisplaySessionsQuery;
import com.museotek.box.application.display.RevokeDisplaySessionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Displays",
        description = "TVs or monitors next to a Box. A manager opens one from the CMS; the display page then runs on "
                + "a token of its own, tied to that one Box.")
@RestController
@RequestMapping("/api/v1/organisations/{orgId}/boxes/{boxId}/display-sessions")
public class DisplaySessionController {

    private final CreateDisplaySessionUseCase createUseCase;
    private final ListDisplaySessionsQuery listQuery;
    private final RevokeDisplaySessionUseCase revokeUseCase;

    public DisplaySessionController(CreateDisplaySessionUseCase createUseCase, ListDisplaySessionsQuery listQuery,
                                    RevokeDisplaySessionUseCase revokeUseCase) {
        this.createUseCase = createUseCase;
        this.listQuery = listQuery;
        this.revokeUseCase = revokeUseCase;
    }

    @Operation(summary = "Open display: create a display token for this Box",
            description = "Manager only. No body. The response holds the token once: it can't be read again. The "
                    + "display page keeps it and uses it instead of the manager's login. It stops working after "
                    + "museotek.display.session-lifetime without use (30 days by default) or when revoked.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedDisplaySessionResponse create(@PathVariable UUID orgId, @PathVariable Long boxId) {
        return CreatedDisplaySessionResponse.from(createUseCase.execute(orgId, boxId));
    }

    @Operation(summary = "List the Box's displays",
            description = "Any org member. Displays that still work (not revoked, not expired), oldest first, without "
                    + "their tokens.")
    @GetMapping
    public List<DisplaySessionResponse> list(@PathVariable UUID orgId, @PathVariable Long boxId) {
        return listQuery.execute(orgId, boxId).stream().map(DisplaySessionResponse::from).toList();
    }

    @Operation(summary = "Revoke a display",
            description = "Manager only. Its token stops working and a connected display is disconnected. "
                    + "404 DISPLAY_SESSION_NOT_FOUND if it's unknown or already revoked.")
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID orgId, @PathVariable Long boxId, @PathVariable UUID sessionId) {
        revokeUseCase.execute(orgId, boxId, sessionId);
    }
}
