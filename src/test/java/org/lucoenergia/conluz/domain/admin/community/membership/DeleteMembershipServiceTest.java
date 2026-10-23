package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipLastActiveAdminException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipOwnsSuppliesException;
import org.lucoenergia.conluz.domain.admin.community.lock.LockCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.DeleteMembershipServiceImpl;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteMembershipServiceTest {

    @Mock
    private DeleteMembershipRepository deleteMembershipRepository;
    @Mock
    private LockCommunityRepository lockCommunityRepository;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;

    private DeleteMembershipService service() {
        return new DeleteMembershipServiceImpl(deleteMembershipRepository, lockCommunityRepository,
                lockUserRepository, lockMembershipRepository, getMembershipsRepository, getSupplyRepository);
    }

    @Test
    void delete_delegatesToRepository() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        locked(communityId, userId, member());

        service().delete(communityId, userId);

        verify(deleteMembershipRepository).delete(communityId, userId);
    }

    @Test
    void mem029_delete_locksTheUserThenTheMembershipBeforeCheckingSupplies() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserId user = UserId.of(userId);
        locked(communityId, userId, member());

        service().delete(communityId, userId);

        InOrder order = inOrder(lockUserRepository, lockMembershipRepository, getSupplyRepository,
                deleteMembershipRepository);
        order.verify(lockUserRepository).lock(user);
        order.verify(lockMembershipRepository).lock(user, communityId);
        order.verify(getSupplyRepository).existsByOwnerAndCommunity(user, communityId);
        order.verify(deleteMembershipRepository).delete(communityId, userId);
    }

    @Test
    void mem029_delete_withSupplyOwnerInTheCommunity_throwsAndDeletesNothing() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserId user = UserId.of(userId);
        locked(communityId, userId, member());
        when(getSupplyRepository.existsByOwnerAndCommunity(user, communityId)).thenReturn(true);

        assertThrows(MembershipOwnsSuppliesException.class, () -> service().delete(communityId, userId));

        verifyNoInteractions(deleteMembershipRepository);
    }

    @Test
    void delete_withoutMembership_throwsNotFoundAndDeletesNothing() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(lockCommunityRepository.lock(communityId)).thenReturn(true);
        when(lockUserRepository.lock(UserId.of(userId))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(userId), communityId)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().delete(communityId, userId));

        verifyNoInteractions(getSupplyRepository, deleteMembershipRepository);
    }

    @Test
    void delete_withUnknownUser_throwsNotFoundAndDeletesNothing() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(lockCommunityRepository.lock(communityId)).thenReturn(true);
        when(lockUserRepository.lock(UserId.of(userId))).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().delete(communityId, userId));

        verifyNoInteractions(lockMembershipRepository, getSupplyRepository, deleteMembershipRepository);
    }

    @Test
    @DisplayName("MEM-033 a deletion locks the community, then the user, then the membership, before reading it")
    void delete_locksTheCommunityThenTheUserThenTheMembership() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserId user = UserId.of(userId);
        locked(communityId, userId, admin(true));
        when(getMembershipsRepository.countActiveAdmins(communityId)).thenReturn(2L);

        service().delete(communityId, userId);

        InOrder order = inOrder(lockCommunityRepository, lockUserRepository, lockMembershipRepository,
                getMembershipsRepository, getSupplyRepository, deleteMembershipRepository);
        order.verify(lockCommunityRepository).lock(communityId);
        order.verify(lockUserRepository).lock(user);
        order.verify(lockMembershipRepository).lock(user, communityId);
        order.verify(getMembershipsRepository).findByUserIdAndCommunityId(userId, communityId);
        order.verify(getMembershipsRepository).countActiveAdmins(communityId);
        order.verify(getSupplyRepository).existsByOwnerAndCommunity(user, communityId);
        order.verify(deleteMembershipRepository).delete(communityId, userId);
    }

    @Test
    @DisplayName("MEM-033 deleting the membership of the last active admin is refused, and nothing is deleted")
    void delete_refusesTheLastActiveAdmin() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        locked(communityId, userId, admin(true));
        when(getMembershipsRepository.countActiveAdmins(communityId)).thenReturn(1L);

        assertThrows(MembershipLastActiveAdminException.class, () -> service().delete(communityId, userId));

        verifyNoInteractions(deleteMembershipRepository);
    }

    @Test
    @DisplayName("MEM-033 MEM-029 the last active admin who owns supplies is refused as the last active admin, "
            + "before the supplies are checked")
    void delete_refusesTheLastActiveAdmin_beforeCheckingSupplies() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        locked(communityId, userId, admin(true));
        when(getMembershipsRepository.countActiveAdmins(communityId)).thenReturn(1L);

        assertThrows(MembershipLastActiveAdminException.class, () -> service().delete(communityId, userId));

        verifyNoInteractions(getSupplyRepository, deleteMembershipRepository);
    }

    @Test
    @DisplayName("MEM-033 deleting the membership of an admin whose account is disabled is not refused, and the "
            + "admins are not counted")
    void delete_deletesAnAdminWithADisabledAccount() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        locked(communityId, userId, admin(false));

        service().delete(communityId, userId);

        verify(getMembershipsRepository, never()).countActiveAdmins(any());
        verify(deleteMembershipRepository).delete(communityId, userId);
    }

    private void locked(UUID communityId, UUID userId, CommunityMembership current) {
        when(lockCommunityRepository.lock(communityId)).thenReturn(true);
        when(lockUserRepository.lock(UserId.of(userId))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(userId), communityId)).thenReturn(true);
        when(getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId))
                .thenReturn(Optional.of(current));
    }

    private static CommunityMembership member() {
        return new CommunityMembership.Builder().withRole(CommunityRole.COMMUNITY_MEMBER).build();
    }

    private static CommunityMembership admin(boolean accountEnabled) {
        User user = UserMother.randomUser();
        user.setEnabled(accountEnabled);
        return new CommunityMembership.Builder().withUser(user).withRole(CommunityRole.COMMUNITY_ADMIN)
                .withAcceptedAt(Instant.parse("2026-01-01T00:00:00Z")).withEnabled(true).build();
    }
}
