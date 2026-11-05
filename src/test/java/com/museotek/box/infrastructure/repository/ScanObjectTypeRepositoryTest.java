package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The universal / Exit flags (changesets 0030-0031) against real Postgres + Liquibase.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ScanObjectTypeRepositoryTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ScanObjectTypeRepository scanObjectTypeRepository;

    private final UUID orgId = UUID.randomUUID();

    @Test
    void flags_areStoredAndTheExitIsFoundPerOrg() {
        em.persist(type(orgId, "Right", true, false));
        em.persist(type(orgId, "Exit", false, true));
        em.persist(type(UUID.randomUUID(), "Red", false, false));
        em.flush();
        em.clear();

        assertThat(scanObjectTypeRepository.findByOrgId(orgId))
                .extracting(ScanObjectType::getName, ScanObjectType::isUniversal, ScanObjectType::isExit)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Right", true, false),
                        org.assertj.core.groups.Tuple.tuple("Exit", false, true));
        assertThat(scanObjectTypeRepository.existsByOrgIdAndExitTrue(orgId)).isTrue();
        assertThat(scanObjectTypeRepository.existsByOrgIdAndExitTrue(UUID.randomUUID())).isFalse();
    }

    @Test
    void secondExitInTheSameOrg_isRejectedByTheDatabase() {
        em.persist(type(orgId, "Exit", false, true));
        em.persist(type(UUID.randomUUID(), "Other org's exit", false, true));
        em.flush();

        // IDENTITY ids: the insert runs on persist, not on flush.
        assertThatThrownBy(() -> em.persistAndFlush(type(orgId, "Second exit", false, true)))
                .hasMessageContaining("uk_scan_object_types_exit_per_org");
    }

    private static ScanObjectType type(UUID orgId, String name, boolean universal, boolean exit) {
        ScanObjectType type = new ScanObjectType();
        type.setOrgId(orgId);
        type.setName(name);
        type.setUniversal(universal);
        type.setExit(exit);
        return type;
    }
}
