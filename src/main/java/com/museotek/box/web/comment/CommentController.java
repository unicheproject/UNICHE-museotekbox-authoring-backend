package com.museotek.box.web.comment;

import com.museotek.box.application.comment.AddCommentUseCase;
import com.museotek.box.application.comment.AddReplyUseCase;
import com.museotek.box.application.comment.CommentThread;
import com.museotek.box.application.comment.ListCommentThreadsQuery;
import com.museotek.box.domain.comment.Comment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Comments",
        description = "Comments testers leave on an experience, in threads with one level of replies.")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/comments")
public class CommentController {

    public record CreateCommentRequest(
            @NotBlank @Size(max = 2000) String body,
            @Size(max = 64) String sceneKey,
            @Size(max = 64) String stepKey
    ) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 2000) String body) {
    }

    private final AddCommentUseCase addCommentUseCase;
    private final AddReplyUseCase addReplyUseCase;
    private final ListCommentThreadsQuery listCommentThreadsQuery;

    public CommentController(
            AddCommentUseCase addCommentUseCase,
            AddReplyUseCase addReplyUseCase,
            ListCommentThreadsQuery listCommentThreadsQuery
    ) {
        this.addCommentUseCase = addCommentUseCase;
        this.addReplyUseCase = addReplyUseCase;
        this.listCommentThreadsQuery = listCommentThreadsQuery;
    }

    @Operation(summary = "List the experience's comment threads, most recent activity first",
            description = "Optional filters: scene (a scene key), status (open, resolved or all), since (an ISO "
                    + "timestamp: only threads with something new after it, for refreshing every few seconds).")
    @GetMapping
    public List<CommentThreadResponse> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since
    ) {
        List<CommentThread> threads = listCommentThreadsQuery.execute(projectId, scene, status, since);
        return threads.stream()
                .map(CommentThreadResponse::from)
                .toList();
    }

    @Operation(summary = "Start a comment thread",
            description = "sceneKey: the screen it's about (leave out for the whole visit). stepKey: the step of the "
                    + "flow it's about. Body up to 2000 characters.")
    @PostMapping
    public ResponseEntity<CommentThreadResponse> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateCommentRequest request
    ) {
        Comment comment = addCommentUseCase.execute(projectId, request.body(), request.sceneKey(), request.stepKey());
        CommentThreadResponse response = CommentThreadResponse.from(new CommentThread(comment, List.of()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Reply to a comment thread",
            description = "Replies are one level deep: reply to the thread's first comment. Body up to 2000 characters.")
    @PostMapping("/{commentId}/replies")
    public ResponseEntity<CommentResponse> reply(
            @PathVariable UUID projectId, @PathVariable UUID commentId, @Valid @RequestBody ReplyRequest request
    ) {
        Comment reply = addReplyUseCase.execute(projectId, commentId, request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(reply));
    }
}
