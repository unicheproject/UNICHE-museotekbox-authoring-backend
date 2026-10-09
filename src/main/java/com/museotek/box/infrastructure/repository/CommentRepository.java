package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.comment.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    // Scoped by construction: a comment id from another experience is simply not found.
    Optional<Comment> findByIdAndProjectId(UUID id, UUID projectId);

    List<Comment> findByParentIdOrderByCreatedAtAsc(UUID parentId);

    interface OpenThreadCount {
        UUID getProjectId();

        long getOpenThreads();
    }

    // Open = an unresolved thread start; replies don't count on their own.
    @Query("""
            SELECT c.projectId AS projectId, COUNT(c) AS openThreads
            FROM Comment c
            WHERE c.projectId IN :projectIds AND c.parentId IS NULL AND c.resolved = false
            GROUP BY c.projectId
            """)
    List<OpenThreadCount> countOpenThreads(@Param("projectIds") Collection<UUID> projectIds);
}
