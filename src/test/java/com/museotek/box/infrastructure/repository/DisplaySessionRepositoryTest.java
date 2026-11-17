package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The display_sessions table (changeset 0036) against real Postgres + Liquibase.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DisplaySessionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Autowired
    private TestEntityManager em;
    @Autowired
    private DisplaySessionRepository sessionRepository;
    @Autowired
    private BoxRepository boxRepository;

    @Test
    void findActive_leavesOutRevokedExpiredAndOtherBoxes_oldestFirst() {
        Box box = box("A");
        Box other = box("B");
        DisplaySession newer = session(box, "newer", NOW.minusSeconds(10), NOW.plus(Duration.ofDays(1)));
        DisplaySession older = session(box, "older", NOW.minusSeconds(20), NOW.plus(Duration.ofDays(1)));
        session(box, "expired", NOW.minusSeconds(30), NOW);
        session(box, "revoked", NOW.minusSeconds(40), NOW.plus(Duration.ofDays(1))).setRevokedAt(NOW);
        session(other, "other", NOW.minusSeconds(50), NOW.plus(Duration.ofDays(1)));
        em.flush();

        assertThat(sessionRepository.findActive(box.getId(), NOW))
                .extracting(DisplaySession::getId)
                .containsExactly(older.getId(), newer.getId());
    }

    @Test
    void findByTokenHash_leavesOutRevoked() {
        Box box = box("A");
        session(box, "live", NOW, NOW.plus(Duration.ofDays(1)));
        session(box, "revoked", NOW, NOW.plus(Duration.ofDays(1))).setRevokedAt(NOW);
        em.flush();

        assertThat(sessionRepository.findByTokenHashAndRevokedAtIsNull("live")).isPresent();
        assertThat(sessionRepository.findByTokenHashAndRevokedAtIsNull("revoked")).isEmpty();
    }

    @Test
    void deletingTheBoxsSessionsFirst_letsTheBoxBeDeleted() {
        Box box = box("A");
        DisplaySession live = session(box, "live", NOW, NOW.plus(Duration.ofDays(1)));
        session(box, "revoked", NOW, NOW.plus(Duration.ofDays(1))).setRevokedAt(NOW);
        em.flush();

        assertThat(sessionRepository.findNotRevokedIds(box.getId())).containsExactly(live.getId());
        assertThat(sessionRepository.deleteByBoxId(box.getId())).isEqualTo(2);
        boxRepository.delete(box);
        em.flush();

        assertThat(boxRepository.findById(box.getId())).isEmpty();
        assertThat(sessionRepository.count()).isZero();
    }

    private Box box(String name) {
        Box box = new Box();
        box.setOrgId(UUID.randomUUID());
        box.setName(name);
        box.setSerialNumber("SN-" + UUID.randomUUID());
        return em.persist(box);
    }

    private DisplaySession session(Box box, String tokenHash, Instant createdAt, Instant expiresAt) {
        DisplaySession session = new DisplaySession();
        session.setId(UUID.randomUUID());
        session.setBoxId(box.getId());
        session.setTokenHash(tokenHash);
        session.setCreatedAt(createdAt);
        session.setExpiresAt(expiresAt);
        return em.persist(session);
    }
}
