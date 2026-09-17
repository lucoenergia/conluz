package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.SupplyAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Black-box characterization of {@link SupplyAccessGuardImpl}: the caller's memberships are real
 * {@link CommunityMembership} objects on a real {@link User} and the helper is the real one, so the
 * membership rules are exercised rather than stubbed. Only {@link AuthService} and the repositories
 * are mocked.
 *
 * <p>Every test here mirrors one in {@code SupplyAccessGuardImplTest} — same name, same assertion —
 * so the two can be matched one to one while both exist.</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplyAccessGuardImplBehaviourTest {

    @Mock
    private AuthService authService;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;

    private SupplyAccessGuard guard() {
        return new SupplyAccessGuardImpl(new CommunityAccessGuardHelper(authService, getCommunityRepository),
                getSupplyRepository);
    }

    // --- canReadSupply ---
    // Reading and editing a supply require the same access (community admin or owner), so any
    // authenticated caller who cannot see the supply gets a 404, never a 403.

    @Test
    void canReadSupply_throwsNotFound_whenUserIsPlatformAdminButNotCommunityAdminOrOwner() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        Supply supply = supplyOwnedBy(UUID.randomUUID());
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_returnsTrue_whenUserIsOwner() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyOwnedBy(user.getId());
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_throwsNotFound_whenUserIsMemberOfSupplyCommunityButNotCommunityAdminOrOwner() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_throwsNotFound_whenUserIsNotMemberOfSupplyCommunity() {
        Community communityB = CommunityMother.random().build();
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), communityB);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadSupply(UUID.randomUUID()));
    }

    @Test
    void canReadSupply_throwsNotFound_whenPlatformAdminAndSupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supplyId));
    }

    @Test
    void canReadSupply_throwsNotFound_whenNonAdminAndSupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supplyId));
    }

    @Test
    void canReadSupply_returnsFalse_whenSupplyIdIsNull() {
        // Null argument short-circuits to false without touching the repository -- never a 404.
        assertFalse(guard().canReadSupply(null));
    }

    @Test
    void canReadSupply_throwsNotFound_whenMembershipInSupplyCommunityIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supply.getId()));
    }

    // --- canEditSupply ---

    @Test
    void canEditSupply_throwsNotFound_whenUserIsPlatformAdminButNotCommunityAdminOrOwner() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_returnsFalse_whenUserIsOwner() {
        User user = UserMother.randomUser();
        Supply supply = supplyOwnedBy(user.getId());
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertFalse(guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_throwsNotFound_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_throwsNotFound_whenPlatformAdminAndSupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canEditSupply(supplyId));
    }

    @Test
    void canEditSupply_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canEditSupply(UUID.randomUUID()));
    }

    @Test
    void canEditSupply_returnsFalse_whenSupplyIdIsNull() {
        assertFalse(guard().canEditSupply(null));
    }

    @Test
    void canEditSupply_returnsTrue_whenUserIsBothOwnerAndCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(user.getId(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canEditSupply(supply.getId()));
    }

    // --- isCommunityAdminOfSupply ---
    // A question, not a gate: every denial is false, never an exception. A controller calls it after
    // canReadSupply has already decided the request proceeds, so throwing here would turn a shaping
    // decision into a second, competing access decision.

    @Test
    void isCommunityAdminOfSupply_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().isCommunityAdminOfSupply(supply.getId()));
    }

    @Test
    void isCommunityAdminOfSupply_returnsTrue_whenUserIsBothOwnerAndCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(user.getId(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        // Being the owner as well must not demote the caller: admin decides what they may see.
        assertTrue(guard().isCommunityAdminOfSupply(supply.getId()));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenUserIsOnlyTheOwner() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(user.getId(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertFalse(guard().isCommunityAdminOfSupply(supply.getId()));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenUserIsAPlainMemberOfTheSupplyCommunity() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_MEMBER, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertFalse(guard().isCommunityAdminOfSupply(supply.getId()));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenUserAdministersAnotherCommunity() {
        Community supplyCommunity = CommunityMother.random().build();
        // Admin somewhere else: the rule is scoped to the community the supply belongs to.
        User user = userWithMembership(CommunityMother.random().build(), CommunityRole.COMMUNITY_ADMIN, true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), supplyCommunity);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertFalse(guard().isCommunityAdminOfSupply(supply.getId()));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenUserIsPlatformAdminButNotCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertFalse(guard().isCommunityAdminOfSupply(supply.getId()));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenSupplyDoesNotExist() {
        UUID supplyId = UUID.randomUUID();
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertFalse(guard().isCommunityAdminOfSupply(supplyId));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenNoAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().isCommunityAdminOfSupply(UUID.randomUUID()));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenSupplyIdIsNull() {
        assertFalse(guard().isCommunityAdminOfSupply(null));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenAdminMembershipInSupplyCommunityIsDisabled() {
        Community community = CommunityMother.random().build();
        User user = userWithMembership(community, CommunityRole.COMMUNITY_ADMIN, false);
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertFalse(guard().isCommunityAdminOfSupply(supply.getId()));
    }

    @Test
    void isCommunityAdminOfSupply_returnsFalse_whenSupplyHasNoCommunity() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyOwnedBy(user.getId());
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertFalse(guard().isCommunityAdminOfSupply(supply.getId()));
    }

    // --- helpers ---

    private User userWithMembership(Community community, CommunityRole communityRole, boolean enabled) {
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community)
                .withRole(communityRole)
                .withEnabled(enabled)
                .build();
        user.setMemberships(List.of(membership));
        return user;
    }

    private Supply supplyOwnedBy(UUID ownerId) {
        User owner = new User.Builder().id(ownerId).build();
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }

    private Supply supplyInCommunity(UUID ownerId, Community community) {
        User owner = new User.Builder().id(ownerId).build();
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withCommunity(community)
                .withName("Supply")
                .withAddress("Address")
                .withEnabled(true)
                .build();
    }
}
