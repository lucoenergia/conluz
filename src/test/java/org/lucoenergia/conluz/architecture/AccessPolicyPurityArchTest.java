package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.base.DescribedPredicate;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing that the access policies stay pure.
 *
 * <p>A policy decides an access question over entities it is handed. It must not load anything,
 * must not know it is running inside Spring, and must not throw: the same rule has to be evaluable
 * both during {@code @PreAuthorize} on one request, and by a capability assembler over a page of
 * already-loaded entities, where a repository call per item would be an N+1 and an exception would
 * be the wrong answer shape entirely.</p>
 *
 * <p>Concretely, nothing in {@code ..admin.community.access.policy..} may depend on Spring, on any
 * repository, on {@link AuthService}, or on an exception type.</p>
 */
public class AccessPolicyPurityArchTest extends BaseArchTest {

    private static final String POLICY_PACKAGE = "..admin.community.access.policy..";
    private static final String PROJECT_PACKAGE = "org.lucoenergia..";
    private static final String ACCESS_DENIED_EXCEPTION =
            "org.springframework.security.access.AccessDeniedException";

    /**
     * Exception types a policy must not touch. Restricted to this project's own throwables plus
     * {@code AccessDeniedException}, rather than every {@link Throwable}: javac synthesises classes
     * that reference JDK errors on their own account — a {@code switch} over {@code AccessDecision}
     * in another class produces a {@code $SwitchMap} holder that references {@code NoSuchFieldError}
     * — and a blanket rule would fail on code nobody wrote.
     */
    private static final DescribedPredicate<JavaClass> FORBIDDEN_EXCEPTIONS =
            DescribedPredicate.describe("project exceptions or " + ACCESS_DENIED_EXCEPTION,
                    javaClass -> ACCESS_DENIED_EXCEPTION.equals(javaClass.getFullName())
                            || (assignableTo(Throwable.class).test(javaClass)
                                    && resideInAPackage(PROJECT_PACKAGE).test(javaClass)));

    @Test
    void accessPoliciesMustNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage(POLICY_PACKAGE)
                .and().areNotAnonymousClasses()
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("a policy must be evaluable outside a request, so it cannot know about Spring")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void accessPoliciesMustNotDependOnRepositoriesOrTheAuthService() {
        noClasses()
                .that().resideInAPackage(POLICY_PACKAGE)
                .and().areNotAnonymousClasses()
                .should().dependOnClassesThat().haveNameMatching(".*Repository(Database|Influx)?")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(AuthService.class.getName())
                .because("a policy decides over entities it is handed; loading them per item would be "
                        + "an N+1 in the capability assembler that evaluates the same rule over a page")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void accessPoliciesMustNotDependOnExceptions() {
        noClasses()
                .that().resideInAPackage(POLICY_PACKAGE)
                .and().areNotAnonymousClasses()
                .should().dependOnClassesThat(FORBIDDEN_EXCEPTIONS)
                .because("a policy returns an AccessDecision; turning a denial into a 404 or a 403 is "
                        + "the guard adapter's job, and a capability assembler must be able to ask "
                        + "the same question without catching anything")
                .check(IMPORTED_CLASSES);
    }
}
