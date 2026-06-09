package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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

    static {
        // Add classes exempt from the @Transactional requirement, if any.
    }

    @Test
    void repositoryDatabaseClassesAreTransactional() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        classes()
                .that().haveSimpleNameEndingWith("RepositoryDatabase")
                .and().haveNameNotMatching(nameMatching(EXCEPTIONS))
                .should(beAnnotatedWithTransactional())
                .check(importedClasses);
    }

    private ArchCondition<com.tngtech.archunit.core.domain.JavaClass> beAnnotatedWithTransactional() {
        return new ArchCondition<>("be annotated with @Transactional") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass javaClass, ConditionEvents events) {
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
