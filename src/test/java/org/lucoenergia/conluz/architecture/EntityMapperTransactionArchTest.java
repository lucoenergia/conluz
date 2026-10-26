package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * An entity mapper reads an entity's lazy associations, so it must run inside the transaction that loaded the entity:
 * with {@code spring.jpa.open-in-view} disabled (#480), no session is open anywhere else. A {@code @Transactional} on
 * the mapper itself would not help: outside a transaction it would open a new session, and the entity's proxies would
 * still belong to the closed one. So the transaction is the calling repository's, and a mapper is called only by a
 * {@code *RepositoryDatabase}, which {@link RepositoryTransactionalArchTest} requires to be transactional, or by
 * another mapper.
 */
public class EntityMapperTransactionArchTest extends BaseArchTest {

    // A lambda or an inner class counts as the class it is declared in
    private static final DescribedPredicate<JavaClass> A_REPOSITORY_DATABASE_OR_A_MAPPER = DescribedPredicate.describe(
            "are a *RepositoryDatabase or an *EntityMapper", javaClass -> {
                String name = javaClass.getName().substring(javaClass.getPackageName().length() + 1).split("\\$")[0];
                return name.endsWith("RepositoryDatabase") || name.endsWith("EntityMapper");
            });

    @Test
    void entityMappersAreNotTransactional() {
        noClasses()
                .that().haveSimpleNameEndingWith("EntityMapper")
                .should().beAnnotatedWith(Transactional.class)
                .because("a mapper runs in the transaction of the repository that loaded the entity")
                .check(IMPORTED_CLASSES);
        noMethods()
                .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("EntityMapper")
                .should().beAnnotatedWith(Transactional.class)
                .because("a mapper runs in the transaction of the repository that loaded the entity")
                .check(IMPORTED_CLASSES);
    }

    @Test
    void entityMappersAreCalledOnlyByRepositoriesAndOtherMappers() {
        classes()
                .that().haveSimpleNameEndingWith("EntityMapper")
                .should().onlyHaveDependentClassesThat(A_REPOSITORY_DATABASE_OR_A_MAPPER)
                .because("the calling repository's transaction is what keeps the entity's lazy associations loadable")
                .check(IMPORTED_CLASSES);
    }
}
