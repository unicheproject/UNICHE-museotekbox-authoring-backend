package com.museotek.box.architecture;

import com.museotek.box.architecture.fixture.AuthoringClassUsingThePlayer;
import com.museotek.box.player.boundaryfixture.PlayerClassUsingAQuery;
import com.museotek.box.player.boundaryfixture.PlayerClassUsingARepository;
import com.museotek.box.player.boundaryfixture.PlayerClassUsingAnEntity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the rules of {@link PlayerBoundaryTest} catch what they should, while the real player package is empty. */
class PlayerBoundaryRulesTest {

    @Test
    void aPlayerClassUsingARepository_breaksTheRule() {
        JavaClasses classes = new ClassFileImporter().importClasses(PlayerClassUsingARepository.class);

        assertThat(PlayerBoundaryTest.PLAYER_USES_NO_REPOSITORIES_OR_ENTITIES.evaluate(classes).hasViolation()).isTrue();
    }

    @Test
    void aPlayerClassUsingAnEntity_breaksTheRule() {
        JavaClasses classes = new ClassFileImporter().importClasses(PlayerClassUsingAnEntity.class);

        assertThat(PlayerBoundaryTest.PLAYER_USES_NO_REPOSITORIES_OR_ENTITIES.evaluate(classes).hasViolation()).isTrue();
    }

    @Test
    void aPlayerClassUsingAQueryAndItsView_isFine() {
        JavaClasses classes = new ClassFileImporter().importClasses(PlayerClassUsingAQuery.class);

        assertThat(PlayerBoundaryTest.PLAYER_USES_NO_REPOSITORIES_OR_ENTITIES.evaluate(classes).hasViolation()).isFalse();
    }

    @Test
    void anAuthoringClassUsingThePlayer_breaksTheRule() {
        JavaClasses classes = new ClassFileImporter()
                .importClasses(AuthoringClassUsingThePlayer.class, PlayerClassUsingAQuery.class);

        assertThat(PlayerBoundaryTest.NOTHING_OUTSIDE_DEPENDS_ON_THE_PLAYER.evaluate(classes).hasViolation()).isTrue();
    }
}
