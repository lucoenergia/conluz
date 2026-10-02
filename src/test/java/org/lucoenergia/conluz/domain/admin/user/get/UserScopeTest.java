package org.lucoenergia.conluz.domain.admin.user.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scope only applies what the policy decided; which scope a caller gets is
 * {@code UserAccessPolicyTest}'s concern. These tests pin how a given scope judges users and
 * memberships.
 */
class UserScopeTest {

    private final Community inScope = CommunityMother.random().build();
    private final Community outOfScope = CommunityMother.random().build();
    private final UUID callerId = UUID.randomUUID();
    private final UserScope scope = UserScope.visibleTo(callerId, Set.of(inScope.getId()));

    // --- construction ---

    @Test
    void all_isUnrestrictedWithNoCallerAndNoCommunities() {
        UserScope all = UserScope.all();

        assertTrue(all.isUnrestricted());
        assertNull(all.selfId());
        assertTrue(all.communityIds().isEmpty());
    }

    @Test
    void visibleTo_isRestrictedEvenWithNoCommunities() {
        UserScope onlyTheCaller = UserScope.visibleTo(callerId, Set.of());

        assertFalse(onlyTheCaller.isUnrestricted());
        assertEquals(callerId, onlyTheCaller.selfId());
        assertTrue(onlyTheCaller.communityIds().isEmpty());
    }

    @Test
    void visibleTo_copiesTheCommunities_soLaterChangesToTheArgumentDoNotWidenIt() {
        Set<UUID> communityIds = new HashSet<>(Set.of(inScope.getId()));
        UserScope copied = UserScope.visibleTo(callerId, communityIds);

        communityIds.add(outOfScope.getId());

        assertEquals(Set.of(inScope.getId()), copied.communityIds());
    }

    @Test
    void visibleTo_rejectsANullSetOfCommunities() {
        // null is what "unrestricted" looks like inside; it must not be reachable from here.
        assertThrows(NullPointerException.class, () -> UserScope.visibleTo(callerId, null));
    }

    // --- includes ---

    @Test
    void includes_admitsAnEnabledMemberOfACommunityInScope() {
        assertTrue(scope.includes(userWith(membership(inScope, true))));
    }

    @Test
    void includes_admitsAUserWithAnyOneEnabledMembershipInScope() {
        assertTrue(scope.includes(userWith(membership(outOfScope, true), membership(inScope, true))));
    }

    @Test
    void includes_rejectsAMemberOfOnlyOtherCommunities() {
        assertFalse(scope.includes(userWith(membership(outOfScope, true))));
    }

    @Test
    void includes_rejectsADisabledMembershipInScope() {
        assertFalse(scope.includes(userWith(membership(inScope, false))));
    }

    @Test
    void includes_rejectsAUserWithNoMemberships() {
        assertFalse(scope.includes(userWith()));
    }

    @Test
    void includes_rejectsAUserWhoseMembershipsWereNeverLoaded() {
        User user = UserMother.randomUser();
        user.setMemberships(null);

        assertFalse(scope.includes(user));
    }

    @Test
    void includes_rejectsAMembershipWithNoCommunity() {
        assertFalse(scope.includes(userWith(membershipWithoutCommunity())));
    }

    @Test
    void includes_admitsTheCaller_whateverTheirMemberships() {
        assertTrue(scope.includes(caller()));
    }

    @Test
    void includes_admitsNobodyAsTheCaller_whenThereIsNoCaller() {
        UserScope noCaller = UserScope.visibleTo(null, Set.of(inScope.getId()));

        assertFalse(noCaller.includes(userWith(membership(outOfScope, true))));
    }

    @Test
    void includes_admitsEveryone_whenUnrestricted() {
        assertTrue(UserScope.all().includes(userWith()));
        assertTrue(UserScope.all().includes(userWith(membership(outOfScope, false))));
    }

    // --- includesMembershipOf ---

    @Test
    void includesMembershipOf_admitsAMembershipInAScopeCommunity() {
        CommunityMembership membership = membership(inScope, true);

        assertTrue(scope.includesMembershipOf(userWith(membership), membership));
    }

    @Test
    void includesMembershipOf_rejectsAMembershipInAnotherCommunity() {
        CommunityMembership visible = membership(inScope, true);
        CommunityMembership hidden = membership(outOfScope, true);

        assertFalse(scope.includesMembershipOf(userWith(visible, hidden), hidden));
    }

    @Test
    void includesMembershipOf_judgesTheCommunityNotWhetherTheMembershipIsEnabled() {
        // The row is in scope through another membership; a disabled one in an administered community
        // is still something its admin can see.
        CommunityMembership disabled = membership(inScope, false);

        assertTrue(scope.includesMembershipOf(userWith(membership(inScope, true), disabled), disabled));
    }

    @Test
    void includesMembershipOf_rejectsAMembershipWithNoCommunity() {
        CommunityMembership membership = membershipWithoutCommunity();

        assertFalse(scope.includesMembershipOf(userWith(membership), membership));
    }

    @Test
    void includesMembershipOf_admitsEveryMembershipOfTheCaller() {
        User caller = caller();
        CommunityMembership elsewhere = membership(outOfScope, true);
        caller.setMemberships(List.of(elsewhere));

        assertTrue(scope.includesMembershipOf(caller, elsewhere));
    }

    @Test
    void includesMembershipOf_admitsEveryMembership_whenUnrestricted() {
        CommunityMembership membership = membership(outOfScope, true);

        assertTrue(UserScope.all().includesMembershipOf(userWith(membership), membership));
    }

    // --- value semantics ---

    @Test
    void scopesWithTheSameCallerAndCommunitiesAreEqual() {
        UserScope same = UserScope.visibleTo(callerId, Set.of(inScope.getId()));

        assertEquals(scope, same);
        assertEquals(scope.hashCode(), same.hashCode());
    }

    @Test
    void scopesDifferingInCallerOrCommunitiesAreNotEqual() {
        assertNotEquals(scope, UserScope.visibleTo(UUID.randomUUID(), Set.of(inScope.getId())));
        assertNotEquals(scope, UserScope.visibleTo(callerId, Set.of(outOfScope.getId())));
        assertNotEquals(scope, UserScope.all());
    }

    @Test
    void anEmptyRestrictedScopeIsNotTheUnrestrictedOne() {
        // Both report no communities; only isUnrestricted tells them apart, so equality must too.
        assertNotEquals(UserScope.all(), UserScope.visibleTo(null, Set.of()));
    }

    private User caller() {
        User caller = UserMother.randomUserWithId(callerId);
        caller.setMemberships(List.of());
        return caller;
    }

    private static User userWith(CommunityMembership... memberships) {
        User user = UserMother.randomUser();
        user.setMemberships(List.of(memberships));
        return user;
    }

    private static CommunityMembership membership(Community community, boolean enabled) {
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(enabled)
                .build();
    }

    private static CommunityMembership membershipWithoutCommunity() {
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();
    }
}
