package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.access.SupplyAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyAccessGuardImplTest {

    @Mock
    private CommunityAccessGuardHelper helper;
    @Mock
    private GetSupplyRepository getSupplyRepository;

    private SupplyAccessGuard guard() {
        return new SupplyAccessGuardImpl(helper, getSupplyRepository);
    }

    // --- canReadSupply ---
    // Reading and editing a supply require the same access (community admin or owner), so any
    // authenticated caller who cannot see the supply gets a 404, never a 403.

    @Test
    void canReadSupply_throwsNotFound_whenUserIsPlatformAdminButNotCommunityAdminOrOwner() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));

        Supply supply = supplyOwnedBy(UUID.randomUUID());
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_returnsTrue_whenUserIsOwner() {
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyOwnedBy(user.getId());
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        UUID communityId = community.getId();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasCommunityAdminRoleIn(user, communityId)).thenReturn(true);

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_throwsNotFound_whenUserIsMemberOfSupplyCommunityButNotCommunityAdminOrOwner() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        UUID communityId = community.getId();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasCommunityAdminRoleIn(user, communityId)).thenReturn(false);

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_throwsNotFound_whenUserIsNotMemberOfSupplyCommunity() {
        Community communityB = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), communityB);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supply.getId()));
    }

    @Test
    void canReadSupply_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canReadSupply(UUID.randomUUID()));
    }

    @Test
    void canReadSupply_throwsNotFound_whenPlatformAdminAndSupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supplyId));
    }

    @Test
    void canReadSupply_throwsNotFound_whenNonAdminAndSupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canReadSupply(supplyId));
    }

    // --- canEditSupply ---

    @Test
    void canEditSupply_throwsNotFound_whenUserIsPlatformAdminButNotCommunityAdminOrOwner() {
        Supply supply = supplyOwnedBy(UUID.randomUUID());
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_returnsTrue_whenUserIsOwner() {
        User user = UserMother.randomUser();
        Supply supply = supplyOwnedBy(user.getId());
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        UUID communityId = community.getId();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasCommunityAdminRoleIn(user, communityId)).thenReturn(true);

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertTrue(guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_throwsNotFound_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));

        Supply supply = supplyInCommunity(UUID.randomUUID(), community);
        when(getSupplyRepository.findById(SupplyId.of(supply.getId()))).thenReturn(Optional.of(supply));

        assertThrows(SupplyNotFoundException.class, () -> guard().canEditSupply(supply.getId()));
    }

    @Test
    void canEditSupply_throwsNotFound_whenPlatformAdminAndSupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));
        when(getSupplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> guard().canEditSupply(supplyId));
    }

    @Test
    void canEditSupply_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canEditSupply(UUID.randomUUID()));
    }

    // --- helpers ---

    private Supply supplyOwnedBy(UUID ownerId) {
        User owner = new User.Builder().id(ownerId).build();
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(owner)
                .withName("Supply")
                .withAddress("Address")
                .withPartitionCoefficient(1.0f)
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
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();
    }
}
