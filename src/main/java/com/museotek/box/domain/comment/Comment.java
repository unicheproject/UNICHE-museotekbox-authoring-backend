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
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column
    private UUID parentId;

    @Column
    private String sceneKey;

    @Column
    private String stepKey;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private String authorSubject;

    // The name shown, copied from the users table when the comment was written.
    @Column(nullable = false)
    private String authorName;

    // Thread starts only.
    @Column(nullable = false)
    private boolean resolved;

    @Column
    private String resolvedBy;

    @Column
    private Instant resolvedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
