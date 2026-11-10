package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.screen.ScreenSetupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScreenSetupCodeRepository extends JpaRepository<ScreenSetupCode, UUID> {

    @Query("""
            select c from ScreenSetupCode c
            where c.orgId = :orgId and c.usedAt is null and c.cancelledAt is null and c.expiresAt > :now
            order by c.createdAt desc
            """)
    List<ScreenSetupCode> findOpen(@Param("orgId") UUID orgId, @Param("now") Instant now);

    Optional<ScreenSetupCode> findByCodeHash(String codeHash);

    // One conditional update, so a double click or a second tab can't redeem the same code twice: 1 = ours, 0 = not open.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ScreenSetupCode c set c.usedAt = :now
            where c.codeHash = :codeHash and c.usedAt is null and c.cancelledAt is null and c.expiresAt > :now
            """)
    int markUsed(@Param("codeHash") String codeHash, @Param("now") Instant now);

    // Conditional for the same reason: a code being redeemed right now can't also be cancelled.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ScreenSetupCode c set c.cancelledAt = :now
            where c.id = :id and c.orgId = :orgId and c.usedAt is null and c.cancelledAt is null
            """)
    int cancel(@Param("id") UUID id, @Param("orgId") UUID orgId, @Param("now") Instant now);
}
