package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.architecture.jpacascade.fixture.CascadesAll;
import org.lucoenergia.conluz.architecture.jpacascade.fixture.CascadesRemove;
import org.lucoenergia.conluz.architecture.jpacascade.fixture.CascadesWithoutRemoval;
import org.lucoenergia.conluz.architecture.jpacascade.fixture.RemovesOrphans;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.members;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture test enforcing that no entity association removes rows as a side effect (#438).
 *
 * <p>A cascade that removes ({@code CascadeType.ALL} or {@code CascadeType.REMOVE}) or {@code orphanRemoval}
 * deletes rows that a domain rule may say must never be deleted that way: until #438, deleting a user deleted their
 * supplies, and deleting a supply its plants, whenever the service checks were skipped. Deletions are decided by the
 * services and guarded by the foreign keys. The only associations allowed to remove are those in
 * {@link #ALLOWED}, each with the reason.</p>
 */
public class JpaCascadeArchTest extends BaseArchTest {

    static final String MESSAGE = "Entity associations must not cascade removal (CascadeType.ALL or REMOVE) or "
            + "remove orphans; delete through a repository, behind the service's checks";

    /**
     * The associations allowed to remove, as {@code owner class.member}, with the reason.
     */
    static final Map<String, String> ALLOWED = Map.of(
            SupplyEntity.class.getName() + ".shelly",
            "1:1 detail row (@MapsId) deleted with its supply by SUP-001; its foreign key is ON DELETE CASCADE too",
            SupplyEntity.class.getName() + ".distributor",
            "1:1 detail row (@MapsId) deleted with its supply by SUP-001; its foreign key is ON DELETE CASCADE too",
            SupplyEntity.class.getName() + ".contract",
            "1:1 detail row (@MapsId) deleted with its supply by SUP-001; its foreign key is ON DELETE CASCADE too");

    @Test
    void productionAssociationsDoNotCascadeRemoval() {
        cascadeRule(ALLOWED.keySet()).check(IMPORTED_CLASSES);
    }

    @Test
    void everyAllowedAssociationExistsAndStillRemoves() {
        for (String allowed : ALLOWED.keySet()) {
            int dot = allowed.lastIndexOf('.');
            JavaField field = IMPORTED_CLASSES.get(allowed.substring(0, dot)).getField(allowed.substring(dot + 1));
            assertTrue(removes(field), () -> allowed + " no longer removes: take it out of ALLOWED");
        }
    }

    static Stream<Arguments> fixturesThatRemove() {
        return Stream.of(
                Arguments.of(CascadesAll.class, 4),
                Arguments.of(CascadesRemove.class, 5),
                Arguments.of(RemovesOrphans.class, 2)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixturesThatRemove")
    void theRuleFails_forEveryAssociationThatRemoves(Class<?> fixture, int associations) {
        ArchRule rule = cascadeRule(Set.of());
        JavaClasses classes = new ClassFileImporter().importClasses(fixture);

        EvaluationResult result = rule.evaluate(classes);
        assertEquals(associations, result.getFailureReport().getDetails().size(),
                () -> String.join("\n", result.getFailureReport().getDetails()));

        AssertionError error = assertThrows(AssertionError.class, () -> rule.check(classes));
        assertTrue(error.getMessage().contains(MESSAGE), error::getMessage);
    }

    @Test
    void theRuleFails_unlessTheAssociationIsAllowed() {
        JavaClasses classes = new ClassFileImporter().importClasses(RemovesOrphans.class);
        Set<String> allowed = Set.of(RemovesOrphans.class.getName() + ".oneToMany");

        EvaluationResult result = cascadeRule(allowed).evaluate(classes);

        assertEquals(1, result.getFailureReport().getDetails().size(),
                () -> String.join("\n", result.getFailureReport().getDetails()));
        assertTrue(result.getFailureReport().getDetails().get(0).contains("oneToOne"),
                () -> String.join("\n", result.getFailureReport().getDetails()));
    }

    @Test
    void theRulePasses_forAssociationsThatDoNotRemove() {
        EvaluationResult result = cascadeRule(Set.of())
                .evaluate(new ClassFileImporter().importClasses(CascadesWithoutRemoval.class));

        assertFalse(result.hasViolation(), () -> String.join("\n", result.getFailureReport().getDetails()));
    }

    static ArchRule cascadeRule(Set<String> allowed) {
        return members()
                .that(areAssociations())
                .and(DescribedPredicate.describe("are not allowed to remove",
                        member -> !allowed.contains(member.getOwner().getName() + "." + member.getName())))
                .should(notRemove())
                .as(MESSAGE);
    }

    private static DescribedPredicate<JavaMember> areAssociations() {
        return DescribedPredicate.describe("are entity associations", member ->
                member.isAnnotatedWith(OneToMany.class) || member.isAnnotatedWith(OneToOne.class)
                        || member.isAnnotatedWith(ManyToOne.class) || member.isAnnotatedWith(ManyToMany.class));
    }

    private static ArchCondition<JavaMember> notRemove() {
        return new ArchCondition<>("not cascade removal or remove orphans") {
            @Override
            public void check(JavaMember member, ConditionEvents events) {
                if (removes(member)) {
                    events.add(SimpleConditionEvent.violated(member,
                            member.getFullName() + " cascades removal or removes orphans"));
                }
            }
        };
    }

    private static boolean removes(JavaMember member) {
        List<CascadeType> cascades = Stream.of(
                        member.tryGetAnnotationOfType(OneToMany.class).map(OneToMany::cascade),
                        member.tryGetAnnotationOfType(OneToOne.class).map(OneToOne::cascade),
                        member.tryGetAnnotationOfType(ManyToOne.class).map(ManyToOne::cascade),
                        member.tryGetAnnotationOfType(ManyToMany.class).map(ManyToMany::cascade))
                .flatMap(cascade -> cascade.stream().flatMap(Arrays::stream))
                .toList();
        boolean removesOrphans = member.tryGetAnnotationOfType(OneToMany.class).map(OneToMany::orphanRemoval)
                .orElse(false)
                || member.tryGetAnnotationOfType(OneToOne.class).map(OneToOne::orphanRemoval).orElse(false);
        return cascades.contains(CascadeType.ALL) || cascades.contains(CascadeType.REMOVE) || removesOrphans;
    }
}
