package org.lucoenergia.conluz.domain.admin.user.delete;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.delete.DeleteUserServiceImpl;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteUserServiceTest {

    @Mock
    private DeleteUserRepository deleteUserRepository;
    @Mock
    private GetUserRepository getUserRepository;
    @Mock
    private ManagePlatformAdminRepository platformAdminRepository;
    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private DeleteMembershipRepository deleteMembershipRepository;

    private DeleteUserService service() {
        return new DeleteUserServiceImpl(deleteUserRepository, getUserRepository, platformAdminRepository,
                lockUserRepository, getSupplyRepository, deleteMembershipRepository);
    }

    @Test
    void delete_withNonPlatformAdmin_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(false).build();
        when(lockUserRepository.lock(id)).thenReturn(true);
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));

        service().delete(id);

        verify(deleteUserRepository).delete(id);
    }

    @Test
    void delete_withPlatformAdminAndAnotherAdminExists_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).build();
        when(lockUserRepository.lock(id)).thenReturn(true);
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        when(platformAdminRepository.countPlatformAdmins()).thenReturn(2L);

        service().delete(id);

        verify(deleteUserRepository).delete(id);
    }

    @Test
    void delete_withLastPlatformAdmin_throwsAndDoesNotDelete() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).build();
        when(lockUserRepository.lock(id)).thenReturn(true);
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        when(platformAdminRepository.countPlatformAdmins()).thenReturn(1L);

        assertThrows(LastPlatformAdminException.class, () -> service().delete(id));

        verify(deleteUserRepository, never()).delete(any());
        verifyNoInteractions(deleteMembershipRepository);
    }

    @Test
    void delete_withUnknownUser_throwsUserNotFoundAndDeletesNothing() {
        UserId id = UserId.of(UUID.randomUUID());
        when(lockUserRepository.lock(id)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> service().delete(id));

        verifyNoInteractions(deleteUserRepository, deleteMembershipRepository);
    }

    @Test
    void acc009_delete_locksTheUserBeforeCheckingSupplies_thenDeletesTheMembershipsBeforeTheUser() {
        UserId id = UserId.of(UUID.randomUUID());
        when(lockUserRepository.lock(id)).thenReturn(true);
        when(getUserRepository.findById(id)).thenReturn(Optional.empty());

        service().delete(id);

        InOrder order = inOrder(lockUserRepository, getSupplyRepository, deleteMembershipRepository,
                deleteUserRepository);
        order.verify(lockUserRepository).lock(id);
        order.verify(getSupplyRepository).existsByOwner(id);
        order.verify(deleteMembershipRepository).deleteByUser(id);
        order.verify(deleteUserRepository).delete(id);
    }

    @Test
    void acc009_delete_withSupplyOwner_throwsAndDeletesNothing() {
        UserId id = UserId.of(UUID.randomUUID());
        when(lockUserRepository.lock(id)).thenReturn(true);
        when(getUserRepository.findById(id)).thenReturn(Optional.empty());
        when(getSupplyRepository.existsByOwner(id)).thenReturn(true);

        assertThrows(UserOwnsSuppliesException.class, () -> service().delete(id));

        verifyNoInteractions(deleteUserRepository, deleteMembershipRepository);
    }
}
