package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The domain depends on nothing in infrastructure: infrastructure implements the domain's ports, never the
 * other way round (#414).
 */
public class DomainInfrastructureBoundaryArchTest extends BaseArchTest {

    @Test
    void domainMustNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
                .because("the domain must not depend on infrastructure (web DTOs, JPA, adapters, Spring "
                        + "configuration). When domain code needs something infrastructure provides, introduce a "
                        + "port in the domain and implement it in infrastructure")
                .check(IMPORTED_CLASSES);
    }
}
