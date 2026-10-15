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
import org.lucoenergia.conluz.infrastructure.admin.community.admins.CommunityAdminResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.CreateMembershipRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.GetMembershipsRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.MemberResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.MembershipResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.UpdateMembershipInvitationRepositoryDatabase;
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
 * them: the membership itself, its entity, the mappers between the two, and the responses that show
 * both states.</p>
 *
 * <p>Queries cannot call {@code isActive()}, so they use {@link ActiveMembershipJpql#ACTIVE_MEMBERSHIP},
 * its JPQL spelling. Bulk invitation selects the memberships awaiting acceptance instead, defined once by
 * {@link CommunityMembership#isAwaitingAcceptance()} and spelled {@link ActiveMembershipJpql#AWAITING_ACCEPTANCE}
 * (MEM-012). A query over memberships that mentions either field must contain one of the two spellings.</p>
 */
public class MembershipStateArchTest extends BaseArchTest {

    private static final Pattern MEMBERSHIP_STATE = Pattern.compile("\\benabled\\b|acceptedAt|accepted_at");

    @Test
    @DisplayName("MEM-004 only the membership, its entity, its mappers and the responses that show it read its state")
    void onlyTheMembershipAndItsMappersReadItsState() {
        noClasses()
                .that().doNotBelongToAnyOf(
                        CommunityMembership.class,
                        CommunityMembershipEntity.class,
                        GetMembershipsRepositoryDatabase.class,
                        CreateMembershipRepositoryDatabase.class,
                        UpdateMembershipRoleRepositoryDatabase.class,
                        UpdateMembershipInvitationRepositoryDatabase.class,
                        MembershipResponse.class,
                        MemberResponse.class,
                        CommunityAdminResponse.class)
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
    @DisplayName("MEM-004 MEM-012 every query that filters memberships on their state uses a canonical condition")
    void queriesFilterMembershipsOnACanonicalCondition() {
        methods()
                .that().areAnnotatedWith(Query.class)
                .should(filterMembershipStateOnlyThroughACanonicalCondition())
                .because("a query that tests enabled or the acceptance of a membership must test both, exactly as "
                        + "CommunityMembership.isActive() or CommunityMembership.isAwaitingAcceptance() does")
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

    private static ArchCondition<JavaMethod> filterMembershipStateOnlyThroughACanonicalCondition() {
        return new ArchCondition<>("filter membership state only through ActiveMembershipJpql.ACTIVE_MEMBERSHIP or "
                + "ActiveMembershipJpql.AWAITING_ACCEPTANCE") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String query = method.getAnnotationOfType(Query.class).value();
                boolean filtersMembershipState = query.contains("community_memberships")
                        && MEMBERSHIP_STATE.matcher(query).find();
                boolean usesACanonicalCondition = query.contains(ActiveMembershipJpql.ACTIVE_MEMBERSHIP)
                        || query.contains(ActiveMembershipJpql.AWAITING_ACCEPTANCE);
                if (filtersMembershipState && !usesACanonicalCondition) {
                    events.add(SimpleConditionEvent.violated(method, String.format(
                            "%s filters memberships on their state without ActiveMembershipJpql.ACTIVE_MEMBERSHIP or "
                                    + "ActiveMembershipJpql.AWAITING_ACCEPTANCE: %s",
                            method.getFullName(), query)));
                }
            }
        };
    }
}
