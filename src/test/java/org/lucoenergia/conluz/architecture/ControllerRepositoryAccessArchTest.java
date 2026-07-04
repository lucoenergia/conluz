package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing that controllers stay thin: they must never depend on the repository
 * layer directly.
 *
 * <p>A controller may only (1) enforce authorization via {@code @PreAuthorize}, (2) bind and
 * validate the request, (3) delegate to a single domain service call, and (4) map the service
 * result to the HTTP response. Any data access — and the business logic around it (config gating,
 * dispatch/branching, iteration, orchestration) — must live in a service. Controllers therefore
 * depend only on service interfaces, never on repositories.</p>
 *
 * <p>"Repository" here covers domain repository ports (simple name ending in {@code Repository}) as
 * well as their infrastructure implementations ({@code *RepositoryDatabase}, {@code *RepositoryInflux}).</p>
 */
public class ControllerRepositoryAccessArchTest extends BaseArchTest {

    private static final String REPOSITORY_NAME_PATTERN = ".*Repository(Database|Influx)?";

    @Test
    void controllersMustNotDependOnRepositories() {
        noClasses()
                .that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should().dependOnClassesThat().haveNameMatching(REPOSITORY_NAME_PATTERN)
                .because("controllers must be thin: no repository access and no domain logic. "
                        + "Data access and the business logic around it (config gating, dispatch, "
                        + "iteration, orchestration) belong in a service; controllers depend only on "
                        + "service interfaces")
                .check(IMPORTED_CLASSES);
    }
}
