package com.museotek.box.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps the player package apart from authoring: it goes through queries and use cases (which do the org and access
 * checks and return only outgoing fields), and authoring never depends on it. See docs/communication.md.
 */
class PlayerBoundaryTest {

    static final String PLAYER = "com.museotek.box.player..";

    static final ArchRule PLAYER_USES_NO_REPOSITORIES_OR_ENTITIES = noClasses()
            .that().resideInAPackage(PLAYER)
            .should().dependOnClassesThat().resideInAPackage("com.museotek.box.infrastructure.repository..")
            .orShould().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("the player must go through queries and use cases, which check the org and hide private fields");

    static final ArchRule NOTHING_OUTSIDE_DEPENDS_ON_THE_PLAYER = noClasses()
            .that().resideOutsideOfPackage(PLAYER)
            .should().dependOnClassesThat().resideInAPackage(PLAYER)
            .because("authoring must keep working, and stay splittable, without the player");

    private static final JavaClasses MAIN_CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.museotek.box");

    @Test
    void thePlayerUsesNoRepositoriesOrEntities() {
        PLAYER_USES_NO_REPOSITORIES_OR_ENTITIES.check(MAIN_CODE);
    }

    @Test
    void nothingOutsideThePlayerDependsOnIt() {
        NOTHING_OUTSIDE_DEPENDS_ON_THE_PLAYER.check(MAIN_CODE);
    }
}
