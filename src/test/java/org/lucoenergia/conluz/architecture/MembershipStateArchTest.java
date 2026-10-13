package org.lucoenergia.conluz.architecture;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.infrastructure.admin.community.ActiveMembershipJpql;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.CreateMembershipRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.GetMembershipsRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.MembershipResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.UpdateMembershipRoleRepositoryDatabase;
import org.springframework.data.jpa.repository.Query;

import java.util.regex.Pattern;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Architecture test enforcing that "is this membership active?" is answered in one place.
 *
 * <p>A membership is active when it is accepted and enabled, and only an active membership grants
 * access (ADR-0003 §2). {@link CommunityMembership#isActive()} is the one definition. Code that
 * tested {@code enabled} or the acceptance by hand would drift from it, and could let a pending or
 * disabled membership grant access. So only the classes that carry or expose the two fields may read
 * them: the membership itself, its entity, the mappers between the two, and the roster response that
 * shows both states.</p>
 *
 * <p>Queries cannot call {@code isActive()}, so they use {@link ActiveMembershipJpql#ACTIVE_MEMBERSHIP},
 * its JPQL spelling. A query over memberships that mentions either field must contain it.</p>
 */
public class MembershipStateArchTest extends BaseArchTest {

    private static final Pattern MEMBERSHIP_STATE = Pattern.compile("\\benabled\\b|acceptedAt|accepted_at");

    @Test
    @DisplayName("MEM-004 only the membership, its entity, its mappers and the roster response read its state")
    void onlyTheMembershipAndItsMappersReadItsState() {
        noClasses()
                .that().doNotBelongToAnyOf(
                        CommunityMembership.class,
                        CommunityMembershipEntity.class,
                        GetMembershipsRepositoryDatabase.class,
                        CreateMembershipRepositoryDatabase.class,
                        UpdateMembershipRoleRepositoryDatabase.class,
                        MembershipResponse.class)
                .should().callMethodWhere(
                        target(owner(assignableTo(CommunityMembership.class)
                                .or(assignableTo(CommunityMembershipEntity.class))))
                                .and(target(nameMatching("isEnabled|getAcceptedAt"))))
                .because("whether a membership is active is decided by CommunityMembership.isActive() "
                        + "alone (ADR-0003 §2); testing enabled or the acceptance anywhere else can let a "
                        + "pending or disabled membership grant access")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("MEM-004 every query that filters memberships on their state uses the active-membership condition")
    void queriesFilterMembershipsOnTheActiveCondition() {
        methods()
                .that().areAnnotatedWith(Query.class)
                .should(filterMembershipStateOnlyThroughTheActiveCondition())
                .because("a query that tests enabled or the acceptance of a membership must test both, "
                        + "exactly as CommunityMembership.isActive() does")
                .check(IMPORTED_CLASSES);
    }

    @Test
    @DisplayName("MEM-004 no derived query selects memberships by their state")
    void noDerivedQuerySelectsMembershipsByState() {
        noMethods()
                .that().areDeclaredIn(CommunityMembershipJpaRepository.class)
                .should().haveNameMatching(".*(Enabled|AcceptedAt).*")
                .because("a derived query on one of the two fields cannot apply both; write a @Query with "
                        + "ActiveMembershipJpql.ACTIVE_MEMBERSHIP instead")
                .check(IMPORTED_CLASSES);
    }

    private static ArchCondition<JavaMethod> filterMembershipStateOnlyThroughTheActiveCondition() {
        return new ArchCondition<>("filter membership state only through ActiveMembershipJpql.ACTIVE_MEMBERSHIP") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String query = method.getAnnotationOfType(Query.class).value();
                boolean filtersMembershipState = query.contains("community_memberships")
                        && MEMBERSHIP_STATE.matcher(query).find();
                if (filtersMembershipState && !query.contains(ActiveMembershipJpql.ACTIVE_MEMBERSHIP)) {
                    events.add(SimpleConditionEvent.violated(method, String.format(
                            "%s filters memberships on their state without ActiveMembershipJpql.ACTIVE_MEMBERSHIP: %s",
                            method.getFullName(), query)));
                }
            }
        };
    }
}
