package org.lucoenergia.conluz.domain.admin.user.disable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.disable.DisableUserServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisableUserServiceTest {

    @Mock
    private DisableUserRepository disableUserRepository;
    @Mock
    private GetUserRepository getUserRepository;
    @Mock
    private ManagePlatformAdminRepository platformAdminRepository;

    private DisableUserService service() {
        return new DisableUserServiceImpl(disableUserRepository, getUserRepository, platformAdminRepository);
    }

    @Test
    void disable_withNonPlatformAdmin_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(false).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));

        service().disable(id);

        verify(disableUserRepository).disable(id);
    }

    @Test
    void disable_withPlatformAdminAndAnotherAdminExists_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        when(platformAdminRepository.countPlatformAdmins()).thenReturn(2L);

        service().disable(id);

        verify(disableUserRepository).disable(id);
    }

    @Test
    void disable_withLastPlatformAdmin_throwsAndDoesNotDisable() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        when(platformAdminRepository.countPlatformAdmins()).thenReturn(1L);

        assertThrows(LastPlatformAdminException.class, () -> service().disable(id));

        verify(disableUserRepository, never()).disable(any());
    }

    @Test
    void disable_withUnknownUser_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        when(getUserRepository.findById(id)).thenReturn(Optional.empty());

        service().disable(id);

        verify(disableUserRepository).disable(id);
    }
}
