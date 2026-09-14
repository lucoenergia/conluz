package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

public class RepositoryTransactionalArchTest extends BaseArchTest {

    private static final Set<String> EXCEPTIONS = new HashSet<>();

    // Classes with a class-level @Transactional default that is also flipped per-method purely
    // to toggle readOnly (rather than being split into separate read/write classes). Tracked
    // follow-up debt — do not add new entries; split the class instead.
    private static final Set<String> MIXING_EXCEPTIONS = Set.of(
            "ManagePlatformAdminRepositoryDatabase",
            "GetSharingAgreementRepositoryDatabase",
            "GetSharingAgreementFileRepositoryDatabase"
    );

    @Test
    void repositoryDatabaseClassesAreTransactional() {
        classes()
                .that().haveSimpleNameEndingWith("RepositoryDatabase")
                .and().haveNameNotMatching(nameMatching(EXCEPTIONS))
                .should(beAnnotatedWithTransactional())
                .check(IMPORTED_CLASSES);
    }

    @Test
    void repositoryDatabaseClassesDoNotMixTransactionalModes() {
        classes()
                .that().haveSimpleNameEndingWith("RepositoryDatabase")
                .and().areAnnotatedWith(Transactional.class)
                .and().haveNameNotMatching(nameMatching(MIXING_EXCEPTIONS))
                .should(TransactionalMixingCondition.notMixReadOnlyAndWriteMethods())
                .check(IMPORTED_CLASSES);
    }

    private ArchCondition<JavaClass> beAnnotatedWithTransactional() {
        return new ArchCondition<>("be annotated with @Transactional") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean hasTransactional = javaClass.isAnnotatedWith(Transactional.class);

                if (!hasTransactional) {
                    String message = String.format(
                            "Class %s is a RepositoryDatabase but is not annotated with @Transactional",
                            javaClass.getName());
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        };
    }

    private static String nameMatching(Set<String> exceptions) {
        if (exceptions.isEmpty()) {
            return "$^"; // matches nothing
        }
        return ".*(" + String.join("|", exceptions) + ").*";
    }
}
