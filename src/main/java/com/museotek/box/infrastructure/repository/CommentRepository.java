package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.comment.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    // Scoped by construction: a comment id from another experience is simply not found.
    Optional<Comment> findByIdAndProjectId(UUID id, UUID projectId);
}
