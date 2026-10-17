package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipOwnsSuppliesException;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.DeleteMembershipServiceImpl;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteMembershipServiceTest {

    @Mock
    private DeleteMembershipRepository deleteMembershipRepository;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;

    private DeleteMembershipService service() {
        return new DeleteMembershipServiceImpl(deleteMembershipRepository, lockUserRepository,
                lockMembershipRepository, getSupplyRepository);
    }

    @Test
    void delete_delegatesToRepository() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(lockUserRepository.lock(UserId.of(userId))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(userId), communityId)).thenReturn(true);

        service().delete(communityId, userId);

        verify(deleteMembershipRepository).delete(communityId, userId);
    }

    @Test
    void mem029_delete_locksTheUserThenTheMembershipBeforeCheckingSupplies() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserId user = UserId.of(userId);
        when(lockUserRepository.lock(user)).thenReturn(true);
        when(lockMembershipRepository.lock(user, communityId)).thenReturn(true);

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
        when(lockUserRepository.lock(user)).thenReturn(true);
        when(lockMembershipRepository.lock(user, communityId)).thenReturn(true);
        when(getSupplyRepository.existsByOwnerAndCommunity(user, communityId)).thenReturn(true);

        assertThrows(MembershipOwnsSuppliesException.class, () -> service().delete(communityId, userId));

        verifyNoInteractions(deleteMembershipRepository);
    }

    @Test
    void delete_withoutMembership_throwsNotFoundAndDeletesNothing() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(lockUserRepository.lock(UserId.of(userId))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(userId), communityId)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().delete(communityId, userId));

        verifyNoInteractions(getSupplyRepository, deleteMembershipRepository);
    }

    @Test
    void delete_withUnknownUser_throwsNotFoundAndDeletesNothing() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(lockUserRepository.lock(UserId.of(userId))).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().delete(communityId, userId));

        verifyNoInteractions(lockMembershipRepository, getSupplyRepository, deleteMembershipRepository);
    }
}
