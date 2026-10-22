package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.ManagePlatformAdminAccessServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagePlatformAdminAccessServiceTest {

    @Mock
    private ManagePlatformAdminRepository repository;
    @Mock
    private GetUserRepository getUserRepository;

    private ManagePlatformAdminAccessService service() {
        return new ManagePlatformAdminAccessServiceImpl(repository, getUserRepository);
    }

    @Test
    @DisplayName("ACC-013 an enabled platform admin is revoked while another one is enabled")
    void revoke_enabledAdminWithMoreThanOneEnabledAdmin_delegatesToRepository() {
        UserId id = existing(true, true);
        when(repository.countPlatformAdmins()).thenReturn(2L);

        service().revoke(id);

        verify(repository).revoke(id);
    }

    @Test
    @DisplayName("ACC-013 the last enabled platform admin is not revoked")
    void revoke_lastEnabledAdmin_throwsAndDoesNotRevoke() {
        UserId id = existing(true, true);
        when(repository.countPlatformAdmins()).thenReturn(1L);

        assertThrows(LastPlatformAdminException.class, () -> service().revoke(id));

        verify(repository, never()).revoke(any());
    }

    @Test
    @DisplayName("ACC-013 a disabled platform admin is revoked even while only one platform admin is enabled")
    void revoke_disabledAdminWithOneEnabledAdmin_delegatesToRepository() {
        UserId id = existing(true, false);
        when(repository.countPlatformAdmins()).thenReturn(1L);

        service().revoke(id);

        verify(repository).revoke(id);
    }

    @Test
    @DisplayName("ACC-013 a user who is not a platform admin is refused before the last-admin rule")
    void revoke_userWhoIsNotAPlatformAdmin_throwsAndDoesNotRevoke() {
        UserId id = existing(false, true);

        assertThrows(UserNotPlatformAdminException.class, () -> service().revoke(id));

        verify(repository, never()).countPlatformAdmins();
        verify(repository, never()).revoke(any());
    }

    @Test
    @DisplayName("ACC-013 an unknown user is refused before the last-admin rule")
    void revoke_unknownUser_throwsAndDoesNotRevoke() {
        UserId id = UserId.of(UserMother.randomUser().getId());
        when(getUserRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service().revoke(id));

        verify(repository, never()).countPlatformAdmins();
        verify(repository, never()).revoke(any());
    }

    private UserId existing(boolean platformAdmin, boolean enabled) {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(platformAdmin);
        user.setEnabled(enabled);
        UserId id = UserId.of(user.getId());
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        return id;
    }
}
