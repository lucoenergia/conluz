package org.lucoenergia.conluz.domain.admin.user.delete;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.delete.DeleteUserServiceImpl;
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

    private DeleteUserService service() {
        return new DeleteUserServiceImpl(deleteUserRepository, getUserRepository, platformAdminRepository);
    }

    @Test
    void delete_withNonPlatformAdmin_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(false).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));

        service().delete(id);

        verify(deleteUserRepository).delete(id);
    }

    @Test
    void delete_withPlatformAdminAndAnotherAdminExists_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        when(platformAdminRepository.countPlatformAdmins()).thenReturn(2L);

        service().delete(id);

        verify(deleteUserRepository).delete(id);
    }

    @Test
    void delete_withLastPlatformAdmin_throwsAndDoesNotDelete() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        when(platformAdminRepository.countPlatformAdmins()).thenReturn(1L);

        assertThrows(LastPlatformAdminException.class, () -> service().delete(id));

        verify(deleteUserRepository, never()).delete(any());
    }

    @Test
    void delete_withUnknownUser_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        when(getUserRepository.findById(id)).thenReturn(Optional.empty());

        service().delete(id);

        verify(deleteUserRepository).delete(id);
    }
}
