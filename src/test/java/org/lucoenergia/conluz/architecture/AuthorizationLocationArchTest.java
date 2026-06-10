package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing that all authorization logic lives in the controller layer.
 *
 * <p>Authorization decisions must be expressed through {@code @PreAuthorize} on controllers
 * (delegating to the {@code @communityAccessGuard} bean). Services and repositories must contain
 * no access-control logic — they only access data. This keeps authorization in a single layer
 * instead of being spread across services and repositories.</p>
 *
 * <p>Concretely this means:</p>
 * <ul>
 *   <li>{@link org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard} (and its
 *       implementation) may only be referenced by controllers and by the guard itself.</li>
 *   <li>Spring Security's {@code AccessDeniedException} may only be referenced by controllers and by
 *       the {@code ConluzAccessDeniedHandler} (the component that maps it to a 403 response). It must
 *       not be thrown from services or repositories.</li>
 * </ul>
 */
public class AuthorizationLocationArchTest extends BaseArchTest {

    private static final String ACCESS_GUARD_PACKAGE = "..admin.community.access..";
    private static final String ACCESS_DENIED_EXCEPTION =
            "org.springframework.security.access.AccessDeniedException";

    @Test
    void onlyControllersMayDependOnTheCommunityAccessGuard() {
        noClasses()
                .that().areNotAnnotatedWith(RestController.class)
                .and().areNotAnnotatedWith(Controller.class)
                .and().resideOutsideOfPackage(ACCESS_GUARD_PACKAGE)
                .should().dependOnClassesThat().haveSimpleNameStartingWith("CommunityAccessGuard")
                .because("authorization must be enforced in controllers via @PreAuthorize; "
                        + "services and repositories must not call the CommunityAccessGuard")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void onlyControllersAndTheAccessDeniedHandlerMayDependOnAccessDeniedException() {
        noClasses()
                .that().areNotAnnotatedWith(RestController.class)
                .and().areNotAnnotatedWith(Controller.class)
                .and().resideOutsideOfPackage(ACCESS_GUARD_PACKAGE)
                .and().haveSimpleNameNotEndingWith("AccessDeniedHandler")
                .should().dependOnClassesThat().haveFullyQualifiedName(ACCESS_DENIED_EXCEPTION)
                .because("services and repositories must not make access-control decisions; "
                        + "AccessDeniedException belongs to the controller layer and its 403 handler")
                .check(IMPORTED_CLASSES);
    }
}
