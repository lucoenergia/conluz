package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.Formatters;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;

import java.util.List;
import java.util.UUID;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture test enforcing that no production class creates an accepted membership without an invitation.
 *
 * <p>{@link CreateMembershipRepository#create(UUID, UUID, CommunityRole)} stores an accepted membership directly,
 * with no inviter and no invitation. Since #447 every membership is registered, imported or invited pending and
 * accepted by the member, so the method only builds test fixtures; a production caller would be a way into a
 * community that skips the invitation.</p>
 */
public class AcceptedMembershipCreationArchTest extends BaseArchTest {

    @Test
    void noProductionClassCreatesAnAcceptedMembershipWithoutAnInvitation() {
        noClasses()
                .should().accessTargetWhere(target(owner(assignableTo(CreateMembershipRepository.class)))
                        .and(target(name("create")))
                        .and(target(takesTheFixtureParameters())))
                .because("a membership is accepted only by its member, after an invitation (#447); "
                        + "CreateMembershipRepository#create(UUID, UUID, CommunityRole) exists for test fixtures")
                .check(IMPORTED_CLASSES);
    }

    // Matched on the method or constructor reached, so both a call and a method reference are caught; a field
    // access has no parameters and never matches.
    private static DescribedPredicate<AccessTarget> takesTheFixtureParameters() {
        List<String> parameters = Formatters.formatNamesOf(UUID.class, UUID.class, CommunityRole.class);
        return DescribedPredicate.describe("parameters (UUID, UUID, CommunityRole)",
                target -> target instanceof AccessTarget.CodeUnitAccessTarget codeUnit
                        && codeUnit.getRawParameterTypes().stream().map(JavaClass::getName).toList().equals(parameters));
    }
}
