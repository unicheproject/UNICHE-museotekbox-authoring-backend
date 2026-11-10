package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.screen.ScreenSetupCode;
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

// The screens tables (changesets 0034, 0035) and the setup codes' conditional updates, against real Postgres + Liquibase.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ScreenSetupCodeRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ScreenSetupCodeRepository setupCodeRepository;

    private final UUID orgId = UUID.randomUUID();

    @Test
    void markUsed_succeedsOnce() {
        code("a", NOW.plus(Duration.ofDays(1)));

        assertThat(setupCodeRepository.markUsed("a", NOW)).isEqualTo(1);
        assertThat(setupCodeRepository.markUsed("a", NOW)).isZero();
        assertThat(setupCodeRepository.findByCodeHash("a").orElseThrow().getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void markUsed_refusesExpiredAndCancelledCodes() {
        code("expired", NOW);
        ScreenSetupCode cancelled = code("cancelled", NOW.plus(Duration.ofDays(1)));

        assertThat(setupCodeRepository.cancel(cancelled.getId(), orgId, NOW)).isEqualTo(1);
        assertThat(setupCodeRepository.markUsed("expired", NOW)).isZero();
        assertThat(setupCodeRepository.markUsed("cancelled", NOW)).isZero();
    }

    @Test
    void cancel_onlyAnOpenCodeOfThatOrg() {
        ScreenSetupCode code = code("a", NOW.plus(Duration.ofDays(1)));

        assertThat(setupCodeRepository.cancel(code.getId(), UUID.randomUUID(), NOW)).isZero();
        setupCodeRepository.markUsed("a", NOW);
        assertThat(setupCodeRepository.cancel(code.getId(), orgId, NOW)).isZero();
    }

    @Test
    void findOpen_leavesOutUsedCancelledAndExpired_newestFirst() {
        ScreenSetupCode older = code("older", NOW.plus(Duration.ofDays(1)));
        ScreenSetupCode newer = code("newer", NOW.plus(Duration.ofDays(2)));
        newer.setCreatedAt(NOW.minusSeconds(10));
        older.setCreatedAt(NOW.minusSeconds(20));
        code("expired", NOW.minusSeconds(1));
        code("used", NOW.plus(Duration.ofDays(1)));
        ScreenSetupCode cancelled = code("cancelled", NOW.plus(Duration.ofDays(1)));
        em.flush();
        setupCodeRepository.markUsed("used", NOW);
        setupCodeRepository.cancel(cancelled.getId(), orgId, NOW);

        assertThat(setupCodeRepository.findOpen(orgId, NOW))
                .extracting(ScreenSetupCode::getCodeHash)
                .containsExactly("newer", "older");
    }

    private ScreenSetupCode code(String hash, Instant expiresAt) {
        ScreenSetupCode code = new ScreenSetupCode();
        code.setId(UUID.randomUUID());
        code.setOrgId(orgId);
        code.setScreenName("Screen " + hash);
        code.setCodeHash(hash);
        code.setCreatedAt(NOW.minusSeconds(60));
        code.setExpiresAt(expiresAt);
        ScreenSetupCode saved = em.persist(code);
        em.flush();
        return saved;
    }
}
