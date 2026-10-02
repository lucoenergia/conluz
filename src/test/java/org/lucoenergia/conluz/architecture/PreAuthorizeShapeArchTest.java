package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Architecture test enforcing that every {@code @PreAuthorize} expression is either
 * {@code isAuthenticated()} or exactly one {@code @communityAccessGuard} call.
 *
 * <p>This is what makes an endpoint's authorization decision nameable. A composite expression —
 * {@code hasRole(..) and !isCurrentUser(..)}, or {@code isAuthenticated() and guard.x(..)} — has no
 * single name, so it cannot be reported to a client as "you may do this", and the same rule cannot
 * be evaluated anywhere other than in front of a request. Every rule belongs behind one guard
 * method, and role checks belong behind the platform guard methods rather than in SpEL.</p>
 *
 * <p>{@code isAuthenticated()} alone stays allowed, but only for endpoints with no object scope
 * (prices, the current user, the community listing), which have nothing to name.</p>
 */
public class PreAuthorizeShapeArchTest extends BaseArchTest {

    private static final Pattern ALLOWED = Pattern.compile(
            "^isAuthenticated\\(\\)$|^@communityAccessGuard\\.\\w+\\([^()]*\\)$");

    @Test
    void everyPreAuthorizeIsIsAuthenticatedOrASingleGuardCall() {
        methods()
                .that().areAnnotatedWith(PreAuthorize.class)
                .should(beIsAuthenticatedOrASingleGuardCall())
                .because("an endpoint's authorization decision must have a single name, so it can be "
                        + "reported as a capability and evaluated outside a request; composites and "
                        + "hasRole(..) belong behind a guard method")
                .check(IMPORTED_CLASSES);
    }

    private static ArchCondition<JavaMethod> beIsAuthenticatedOrASingleGuardCall() {
        return new ArchCondition<>("be isAuthenticated() or a single @communityAccessGuard call") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String expression = method.getAnnotationOfType(PreAuthorize.class).value();
                boolean satisfied = ALLOWED.matcher(expression).matches();
                events.add(new SimpleConditionEvent(method, satisfied, String.format(
                        "%s.%s is annotated @PreAuthorize(\"%s\"), which is neither isAuthenticated() "
                                + "nor a single @communityAccessGuard call",
                        method.getOwner().getSimpleName(), method.getName(), expression)));
            }
        };
    }
}
