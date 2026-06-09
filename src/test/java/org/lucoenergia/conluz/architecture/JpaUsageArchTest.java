package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test to enforce that JPA types (entities and repositories) are only used
 * within repository implementation classes and Spring Data JPA repository interfaces.
 *
 * <p>This enforces Hexagonal Architecture by ensuring JPA types never leak into
 * services, controllers, or the domain layer.</p>
 *
 * <p>Allowed locations for JPA entities and repositories:</p>
 * <ul>
 *   <li>Spring Data JPA repository interfaces ({@code extends JpaRepository})</li>
 *   <li>Repository implementation classes (named {@code *RepositoryImpl},
 *       {@code *RepositoryDatabase}, {@code *RepositoryInflux}, or {@code *RepositoryRest})</li>
 *   <li>Entity mapper classes (named {@code *EntityMapper})</li>
 *   <li>Other entity classes</li>
 * </ul>
 *
 */
public class JpaUsageArchTest extends BaseArchTest {

    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void jpaEntitiesMustResideInInfrastructure() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .should().resideInAnyPackage("..infrastructure..")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void jpaRepositoriesMustResideInInfrastructure() {
        classes()
                .that().areAssignableTo(JpaRepository.class)
                .should().resideInAnyPackage("..infrastructure..")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void noDomainLayerShouldDependOnJpaEntities() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(IMPORTED_CLASSES);
    }

    @Test
    void noDomainLayerShouldDependOnJpaRepositories() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().areAssignableTo(JpaRepository.class)
                .check(IMPORTED_CLASSES);
    }

    @Test
    void servicesShouldNotDependOnJpaEntities() {
        noClasses()
                .that().areAnnotatedWith(Service.class)
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(IMPORTED_CLASSES);
    }

    @Test
    void servicesShouldNotDependOnJpaRepositories() {
        noClasses()
                .that().areAnnotatedWith(Service.class)
                .should().dependOnClassesThat().areAssignableTo(JpaRepository.class)
                .check(IMPORTED_CLASSES);
    }

    @Test
    void controllersShouldNotDependOnJpaEntities() {
        noClasses()
                .that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(IMPORTED_CLASSES);
    }

    @Test
    void controllersShouldNotDependOnJpaRepositories() {
        noClasses()
                .that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should().dependOnClassesThat().areAssignableTo(JpaRepository.class)
                .check(IMPORTED_CLASSES);
    }
}
