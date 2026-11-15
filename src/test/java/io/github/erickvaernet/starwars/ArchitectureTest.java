package io.github.erickvaernet.starwars;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks the dependency rules of {@code docs/ARCHITECTURE.md}.
 *
 * <p>Each test is one rule of that document, in the same order. A rule about a package that has
 * no classes yet passes: the rules are in place from the first commit and start to matter as the
 * code arrives.
 */
class ArchitectureTest {

    private static final String BASE = "io.github.erickvaernet.starwars";
    private static final String AUTH = BASE + ".auth..";
    private static final String CATALOG = BASE + ".catalog..";
    private static final String SWAPI = BASE + ".swapi..";
    private static final String SHARED = BASE + ".shared..";

    /** Production classes only: tests are allowed to reach into any package. */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    @DisplayName("Rule 1: catalog does not know swapi")
    void catalogDoesNotKnowSwapi() {
        noClasses().that().resideInAPackage(CATALOG)
                .should().dependOnClassesThat().resideInAPackage(SWAPI)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("Rule 2: the SWAPI format does not leave swapi")
    void swapiFormatDoesNotLeaveSwapi() {
        noClasses().that().resideOutsideOfPackage(SWAPI)
                .should().dependOnClassesThat().resideInAPackage(SWAPI)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("Rule 3: auth and catalog do not depend on each other")
    void authAndCatalogDoNotDependOnEachOther() {
        noClasses().that().resideInAPackage(AUTH)
                .should().dependOnClassesThat().resideInAPackage(CATALOG)
                .allowEmptyShould(true)
                .check(CLASSES);
        noClasses().that().resideInAPackage(CATALOG)
                .should().dependOnClassesThat().resideInAPackage(AUTH)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("Rule 4: nothing depends on a controller")
    void nothingDependsOnAController() {
        noClasses().that().areNotAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("Rule 5: shared uses no other package")
    void sharedUsesNoOtherPackage() {
        noClasses().that().resideInAPackage(SHARED)
                .should().dependOnClassesThat().resideInAnyPackage(AUTH, CATALOG, SWAPI)
                .allowEmptyShould(true)
                .check(CLASSES);
    }

}
