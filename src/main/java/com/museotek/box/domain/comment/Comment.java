package com.museotek.box.domain.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A comment a tester left on an experience: either a thread start ({@link #parentId} null) or a
 * reply to one (replies are one level deep). A thread start is about a screen ({@link #sceneKey},
 * null = the whole visit) and a step of the flow ({@link #stepKey}); both are stored as given and
 * never cleaned up when scenes change, so a comment is never lost.
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "scene_key", length = 64)
    private String sceneKey;

    @Column(name = "step_key", length = 64)
    private String stepKey;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "author_subject", nullable = false, length = 255)
    private String authorSubject;

    // The name shown, copied from the users table when the comment was written.
    @Column(name = "author_name", nullable = false, length = 255)
    private String authorName;

    // Thread starts only.
    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    // When the thread was last resolved or reopened; counts as activity for "anything new since".
    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    // Thread starts only: when a reply was last deleted; also counts as activity for "anything new since".
    @Column(name = "reply_deleted_at")
    private Instant replyDeletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
