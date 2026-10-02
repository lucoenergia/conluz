package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.UserScope;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCapabilitiesAssemblerTest {

    @Mock
    private GetMembershipsRepository getMembershipsRepository;

    private UserCapabilitiesAssembler assembler() {
        return new UserCapabilitiesAssembler(new AccessPolicies(), getMembershipsRepository);
    }

    // --- the capabilities themselves ---

    @Test
    void aPlatformAdminMayDoEverythingToSomebodyElse() {
        User target = CapabilityFixtures.userWithNoMemberships();

        UserCapabilitiesResponse capabilities =
                assembler().assembleWithLoadedMemberships(CapabilityFixtures.platformAdmin(), target);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanEdit());
        assertTrue(capabilities.isCanDelete());
        assertTrue(capabilities.isCanEnable());
        assertTrue(capabilities.isCanDisable());
        assertTrue(capabilities.isCanGrantPlatformAdmin());
        assertTrue(capabilities.isCanRevokePlatformAdmin());
    }

    /**
     * Nobody may delete, enable or disable themselves, admins included -- and an admin may not strip
     * their own platform-admin flag either. Reading and editing stay open, so these four are the
     * only fields that differ from the case above.
     */
    @Test
    void aPlatformAdminMayNotActOnThemselves() {
        User caller = CapabilityFixtures.platformAdmin();

        UserCapabilitiesResponse capabilities = assembler().assembleWithLoadedMemberships(caller, caller);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanEdit());
        assertFalse(capabilities.isCanDelete());
        assertFalse(capabilities.isCanEnable());
        assertFalse(capabilities.isCanDisable());
        assertFalse(capabilities.isCanRevokePlatformAdmin());
    }

    @Test
    void aCommunityAdminMayAdministerAMemberOfTheirCommunity() {
        Community community = CapabilityFixtures.community();
        User target = CapabilityFixtures.memberOf(community);

        UserCapabilitiesResponse capabilities =
                assembler().assembleWithLoadedMemberships(CapabilityFixtures.adminOf(community), target);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanEdit());
        assertTrue(capabilities.isCanDelete());
        assertTrue(capabilities.isCanListSupplies());
        assertFalse(capabilities.isCanGrantPlatformAdmin());
        assertFalse(capabilities.isCanRevokePlatformAdmin());
    }

    /**
     * An ordinary member editing their own record is a 403 on purpose: name, DNI and member number
     * are an administrative change. Changing one's own contact details goes through
     * PUT /api/v1/users/profile, which carries no capability because any authenticated caller may
     * use it.
     */
    @Test
    void anOrdinaryMemberMayReadThemselvesAndListTheirSuppliesButNotEditThemselves() {
        User caller = CapabilityFixtures.memberOf(CapabilityFixtures.community());

        UserCapabilitiesResponse capabilities = assembler().assembleWithLoadedMemberships(caller, caller);

        assertTrue(capabilities.isCanRead());
        assertTrue(capabilities.isCanListSupplies());
        assertFalse(capabilities.isCanEdit());
        assertFalse(capabilities.isCanDelete());
    }

    /**
     * Reading a user is not reading their supply data: canListSupplies has no platform-admin branch,
     * so an admin who administers none of the target's communities is offered the user but not their
     * supplies.
     */
    @Test
    void aPlatformAdminMayReadAUserWithoutBeingOfferedTheirSupplies() {
        Community community = CapabilityFixtures.community();
        User target = CapabilityFixtures.memberOf(community);

        UserCapabilitiesResponse capabilities =
                assembler().assembleWithLoadedMemberships(CapabilityFixtures.platformAdmin(), target);

        assertTrue(capabilities.isCanRead());
        assertFalse(capabilities.isCanListSupplies());
    }

    @Test
    void aStrangerMayDoNothing() {
        UserCapabilitiesResponse capabilities = assembler()
                .assembleWithLoadedMemberships(CapabilityFixtures.stranger(),
                        CapabilityFixtures.userWithNoMemberships());

        assertFalse(capabilities.isCanRead());
        assertFalse(capabilities.isCanEdit());
        assertFalse(capabilities.isCanDelete());
        assertFalse(capabilities.isCanEnable());
        assertFalse(capabilities.isCanDisable());
        assertFalse(capabilities.isCanGrantPlatformAdmin());
        assertFalse(capabilities.isCanRevokePlatformAdmin());
        assertFalse(capabilities.isCanListSupplies());
    }

    // --- loaded versus fetched ---

    /**
     * The whole reason this class has two families of methods. A user with no memberships is
     * indistinguishable from one whose memberships were never loaded, so the caller says which it
     * is. Asked as loaded, this target really has none, and the answer is a denial -- reached
     * without a query, because "loaded" is taken at its word.
     */
    @Test
    void aTargetWithZeroMembershipsIsTreatedAsLoadedAndEmptyRatherThanAsNotLoaded() {
        Community community = CapabilityFixtures.community();
        User target = CapabilityFixtures.userWithNoMemberships();

        UserCapabilitiesResponse capabilities = assembler()
                .assembleWithLoadedMemberships(CapabilityFixtures.adminOf(community), target);

        assertFalse(capabilities.isCanRead());
        verify(getMembershipsRepository, never()).findByUserIds(anyCollection());
        verify(getMembershipsRepository, never()).findByUserId(any());
    }

    @Test
    void assemblingOverLoadedMembershipsIssuesNoQuery() {
        Community community = CapabilityFixtures.community();
        List<User> targets = List.of(CapabilityFixtures.memberOf(community),
                CapabilityFixtures.memberOf(community), CapabilityFixtures.memberOf(community));

        Map<UUID, UserCapabilitiesResponse> capabilities = assembler()
                .assembleAllWithLoadedMemberships(CapabilityFixtures.adminOf(community), targets);

        assertEquals(3, capabilities.size());
        assertTrue(capabilities.values().stream().allMatch(UserCapabilitiesResponse::isCanRead));
        verify(getMembershipsRepository, never()).findByUserIds(anyCollection());
        verify(getMembershipsRepository, never()).findByUserId(any());
    }

    /**
     * The N+1 this class exists to avoid: one query for the page, not one per user, and the same
     * one query whether the page holds one user or five.
     */
    @Test
    void assemblingOverFetchedMembershipsIssuesExactlyOneQueryWhateverThePageSize() {
        Community community = CapabilityFixtures.community();
        User admin = CapabilityFixtures.adminOf(community);
        List<User> targets = List.of(CapabilityFixtures.userWithNoMemberships(),
                CapabilityFixtures.userWithNoMemberships(), CapabilityFixtures.userWithNoMemberships(),
                CapabilityFixtures.userWithNoMemberships(), CapabilityFixtures.userWithNoMemberships());
        when(getMembershipsRepository.findByUserIds(anyCollection()))
                .thenReturn(membershipsIn(community, targets));

        Map<UUID, UserCapabilitiesResponse> capabilities =
                assembler().assembleAllFetchingMemberships(admin, targets);

        assertEquals(5, capabilities.size());
        assertTrue(capabilities.values().stream().allMatch(UserCapabilitiesResponse::isCanRead));
        verify(getMembershipsRepository, times(1)).findByUserIds(anyCollection());
        verify(getMembershipsRepository, never()).findByUserId(any());
    }

    /**
     * The batch is lazy as well as shared: a platform admin looking at their own record settles
     * every rule from the caller alone, so nothing is loaded at all.
     */
    @Test
    void nothingIsFetchedWhenNoRuleAsksForTheTargetMemberships() {
        User caller = CapabilityFixtures.platformAdmin();

        assembler().assembleAllFetchingMemberships(caller, List.of(caller));

        verify(getMembershipsRepository, never()).findByUserIds(anyCollection());
    }

    /**
     * The batch decides capabilities and nothing else. Writing it back onto the targets would put
     * other people's community memberships into UserResponse.memberships, which serialises as {}
     * for an embedded user today -- a caller who can see somebody only as a supply owner must not
     * learn which communities they belong to.
     */
    @Test
    void theFetchedMembershipsAreNotWrittenBackOntoTheTargets() {
        Community community = CapabilityFixtures.community();
        List<User> targets = List.of(CapabilityFixtures.userWithNoMemberships(),
                CapabilityFixtures.userWithNoMemberships());
        when(getMembershipsRepository.findByUserIds(anyCollection()))
                .thenReturn(membershipsIn(community, targets));

        assembler().assembleAllFetchingMemberships(CapabilityFixtures.adminOf(community), targets);

        for (User target : targets) {
            assertTrue(target.getMemberships().isEmpty(),
                    "the target must still carry no memberships: " + target.getMemberships());
        }
    }

    private Map<UUID, List<CommunityMembership>> membershipsIn(Community community, List<User> targets) {
        return targets.stream().collect(java.util.stream.Collectors.toMap(User::getId,
                target -> List.of(CapabilityFixtures.membershipOf(target, community,
                        CommunityRole.COMMUNITY_MEMBER, true))));
    }

    // --- #336: the capabilities are decided on the full memberships ---

    /**
     * A regression guard, not what makes this safe. GET /users and GET /users/{userId} narrow a
     * user's memberships only when the response is built, after the capabilities have been assembled
     * from the full set, so the narrowing cannot move a capability whatever this test says. This
     * catches someone moving the narrowing earlier, into the service, and finding it then does.
     */
    @Test
    void narrowingTheMembershipsToTheCallersScopeWouldNotChangeAnyCapability() {
        Community a = CapabilityFixtures.community();
        Community b = CapabilityFixtures.community();
        User target = CapabilityFixtures.userWithNoMemberships();
        target.setMemberships(List.of(
                CapabilityFixtures.membershipOf(target, a, CommunityRole.COMMUNITY_MEMBER, true),
                CapabilityFixtures.membershipOf(target, b, CommunityRole.COMMUNITY_MEMBER, true)));
        User adminOfAMemberOfB = CapabilityFixtures.userWithNoMemberships();
        adminOfAMemberOfB.setMemberships(List.of(
                CapabilityFixtures.membershipOf(adminOfAMemberOfB, a, CommunityRole.COMMUNITY_ADMIN, true),
                CapabilityFixtures.membershipOf(adminOfAMemberOfB, b, CommunityRole.COMMUNITY_MEMBER, true)));

        Map<String, User> callers = new LinkedHashMap<>();
        callers.put("platform admin", CapabilityFixtures.platformAdmin());
        callers.put("admin of A", CapabilityFixtures.adminOf(a));
        callers.put("admin of A, member of B", adminOfAMemberOfB);
        callers.put("member of B", CapabilityFixtures.memberOf(b));
        callers.put("the target", target);
        callers.put("stranger", CapabilityFixtures.stranger());

        int narrowed = 0;
        for (Map.Entry<String, User> caller : callers.entrySet()) {
            UserScope scope = new AccessPolicies().user().visibleUsers(caller.getValue());
            User narrowedTarget = CapabilityFixtures.userWithNoMemberships();
            narrowedTarget.setId(target.getId());
            narrowedTarget.setMemberships(target.getMemberships().stream()
                    .filter(m -> scope.includesMembershipOf(target, m)).toList());
            if (narrowedTarget.getMemberships().size() < target.getMemberships().size()) narrowed++;

            assertEquals(flags(assembler().assembleWithLoadedMemberships(caller.getValue(), target)),
                    flags(assembler().assembleWithLoadedMemberships(caller.getValue(), narrowedTarget)),
                    caller.getKey());
        }
        // Some caller must actually lose a membership, or the comparison above compares nothing.
        assertTrue(narrowed > 0);
    }

    private static List<Boolean> flags(UserCapabilitiesResponse c) {
        return List.of(c.isCanRead(), c.isCanEdit(), c.isCanDelete(), c.isCanEnable(), c.isCanDisable(),
                c.isCanGrantPlatformAdmin(), c.isCanRevokePlatformAdmin(), c.isCanListSupplies());
    }
}
