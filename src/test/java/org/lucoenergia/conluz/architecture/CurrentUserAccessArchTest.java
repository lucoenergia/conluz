package org.lucoenergia.conluz.architecture;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing that controllers obtain the authenticated user through
 * {@code @AuthenticationPrincipal}, not by calling {@link AuthService#getCurrentUser()}.
 *
 * <p>Every endpoint that needs the caller's identity is already guarded by {@code @PreAuthorize},
 * which guarantees a non-null, authenticated {@code User} principal by the time the controller
 * method body runs. Calling {@code authService.getCurrentUser().orElseThrow(...)} inside such a
 * method is redundant defensive code — the "or else" branch can never execute — and adds
 * boilerplate that {@code @AuthenticationPrincipal User currentUser} avoids entirely.</p>
 *
 * <p>{@code AuthService.getCurrentUser()} remains legitimate for
 * {@link org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard} implementations,
 * which run as part of the {@code @PreAuthorize} evaluation itself and cannot rely on it having
 * already happened.</p>
 */
public class CurrentUserAccessArchTest extends BaseArchTest {

    @Test
    void controllersMustNotCallAuthServiceGetCurrentUser() {
        noClasses()
                .that().areAnnotatedWith(RestController.class)
                .or().areAnnotatedWith(Controller.class)
                .should().callMethod(AuthService.class, "getCurrentUser")
                .because("@PreAuthorize already guarantees an authenticated principal by the time a controller "
                        + "method runs; controllers must obtain it via @AuthenticationPrincipal instead of "
                        + "authService.getCurrentUser().orElseThrow(...)")
                .check(IMPORTED_CLASSES);
    }
}
