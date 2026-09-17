package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Architecture test enforcing that every handler method carries a {@code @PreAuthorize}.
 *
 * <p>{@link PreAuthorizeShapeArchTest} constrains the expressions that exist; it cannot notice an
 * endpoint that has none. A new handler added without an authorization clause would otherwise be
 * reachable by any authenticated caller, silently, with nothing failing.</p>
 *
 * <p>The only exceptions are the four endpoints the security filter chain declares
 * {@code permitAll()}, listed here by class and method so adding a fifth is a deliberate act.</p>
 */
public class PreAuthorizePresenceArchTest extends BaseArchTest {

    private static final List<Class<? extends Annotation>> MAPPING_ANNOTATIONS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, PatchMapping.class, DeleteMapping.class);

    /**
     * The {@code permitAll()} endpoints in {@code WebSecurityConfig}: logging in and out, the
     * first-run initialisation, and the unauthenticated build-info endpoint.
     */
    private static final Set<String> PERMIT_ALL = Set.of(
            "LoginUserController#login",
            "LogoutUserController#logout",
            "InitController#init",
            "GetInfoController#getInfo");

    @Test
    void everyHandlerMethodIsAuthorized() {
        methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .or().areDeclaredInClassesThat().areAnnotatedWith(Controller.class)
                .should(beAuthorizedUnlessPermitAll())
                .because("an endpoint without an authorization clause is reachable by any "
                        + "authenticated caller; new ones must not be able to appear unnoticed")
                .check(IMPORTED_CLASSES);
    }

    private static ArchCondition<JavaMethod> beAuthorizedUnlessPermitAll() {
        return new ArchCondition<>("carry @PreAuthorize, unless permitted to all") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!isHandler(method)) {
                    return;
                }
                String id = method.getOwner().getSimpleName() + "#" + method.getName();
                boolean satisfied = PERMIT_ALL.contains(id)
                        || method.isAnnotatedWith(PreAuthorize.class);
                events.add(new SimpleConditionEvent(method, satisfied,
                        id + " maps an HTTP request but carries no @PreAuthorize"));
            }

            private boolean isHandler(JavaMethod method) {
                return MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith);
            }
        };
    }
}
