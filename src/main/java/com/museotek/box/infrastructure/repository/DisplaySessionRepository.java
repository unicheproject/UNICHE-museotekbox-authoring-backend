package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.display.DisplaySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisplaySessionRepository extends JpaRepository<DisplaySession, UUID> {

    // The Box's displays that still work: not revoked, not expired. Oldest first.
    @Query("""
            select d from DisplaySession d
            where d.boxId = :boxId and d.revokedAt is null and d.expiresAt > :now
            order by d.createdAt asc
            """)
    List<DisplaySession> findActive(@Param("boxId") Long boxId, @Param("now") Instant now);

    Optional<DisplaySession> findByIdAndBoxIdAndRevokedAtIsNull(UUID id, Long boxId);

    Optional<DisplaySession> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Query("select d.id from DisplaySession d where d.boxId = :boxId and d.revokedAt is null")
    List<UUID> findNotRevokedIds(@Param("boxId") Long boxId);

    // Not clearAutomatically: the Box being deleted in the same transaction must stay managed.
    @Modifying(flushAutomatically = true)
    @Query("delete from DisplaySession d where d.boxId = :boxId")
    int deleteByBoxId(@Param("boxId") Long boxId);
}
