package com.museotek.box.web.comment;

import com.museotek.box.application.comment.CountOpenCommentsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Org-level, unlike the rest of the comment endpoints (which sit under one experience), so it gets its own controller.
@Tag(name = "Comments")
@RestController
public class CommentCountController {

    private final CountOpenCommentsQuery countOpenCommentsQuery;

    public CommentCountController(CountOpenCommentsQuery countOpenCommentsQuery) {
        this.countOpenCommentsQuery = countOpenCommentsQuery;
    }

    @Operation(summary = "Open comment threads per experience of an org",
            description = "One entry per experience the caller can see, with 0 where nothing is open. For the "
                    + "\"N comments\" on the experiences list.")
    @GetMapping("/api/v1/organisations/{orgId}/comment-counts")
    public List<CommentCountResponse> counts(@PathVariable UUID orgId) {
        List<CountOpenCommentsQuery.OpenCount> counts = countOpenCommentsQuery.execute(orgId);
        return counts.stream()
                .map(CommentCountResponse::from)
                .toList();
    }
}
