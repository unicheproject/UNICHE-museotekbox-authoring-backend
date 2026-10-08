package com.museotek.box.application.comment;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.comment.Comment;
import com.museotek.box.domain.comment.InvalidCommentException;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An experience's comment threads, most recent activity first. Filters, all optional:
 * <ul>
 *   <li>{@code sceneKey}: only threads on that screen;</li>
 *   <li>{@code status}: {@code open}, {@code resolved} or {@code all} (default);</li>
 *   <li>{@code since}: only threads with something new after that time - what the frontend's
 *       10-second refresh asks for.</li>
 * </ul>
 * Loads the experience's comments and groups them in memory: an experience has tens to hundreds,
 * not thousands.
 */
@Service
public class ListCommentThreadsQuery {

    private final ProjectAccessGuard projectAccessGuard;
    private final CommentRepository commentRepository;

    public ListCommentThreadsQuery(ProjectAccessGuard projectAccessGuard, CommentRepository commentRepository) {
        this.projectAccessGuard = projectAccessGuard;
        this.commentRepository = commentRepository;
    }

    public List<CommentThread> execute(UUID projectId, String sceneKey, String status, Instant since) {
        String statusFilter = parseStatus(status);
        projectAccessGuard.requireAccess(projectId);

        Map<UUID, Comment> threadStarts = new LinkedHashMap<>();
        Map<UUID, List<Comment>> repliesByThread = new LinkedHashMap<>();
        for (Comment comment : commentRepository.findByProjectIdOrderByCreatedAtAsc(projectId)) {
            if (comment.getParentId() == null) {
                threadStarts.put(comment.getId(), comment);
            } else {
                repliesByThread.computeIfAbsent(comment.getParentId(), id -> new ArrayList<>()).add(comment);
            }
        }

        List<CommentThread> threads = new ArrayList<>();
        for (Comment start : threadStarts.values()) {
            CommentThread thread = new CommentThread(start, repliesByThread.getOrDefault(start.getId(), List.of()));
            if (matches(thread, sceneKey, statusFilter, since)) {
                threads.add(thread);
            }
        }
        threads.sort(Comparator.comparing(CommentThread::lastActivity).reversed());
        return threads;
    }

    private static boolean matches(CommentThread thread, String sceneKey, String status, Instant since) {
        Comment start = thread.comment();
        if (sceneKey != null && !sceneKey.equals(start.getSceneKey())) {
            return false;
        }
        if (status.equals("open") && start.isResolved()) {
            return false;
        }
        if (status.equals("resolved") && !start.isResolved()) {
            return false;
        }
        return since == null || thread.lastActivity().isAfter(since);
    }

    private static String parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return "all";
        }
        String normalized = status.toLowerCase();
        if (!normalized.equals("open") && !normalized.equals("resolved") && !normalized.equals("all")) {
            throw new InvalidCommentException("status '" + status + "' is not valid (expected open, resolved or all)");
        }
        return normalized;
    }
}
