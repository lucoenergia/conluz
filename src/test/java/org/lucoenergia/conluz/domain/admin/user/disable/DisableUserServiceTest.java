package org.lucoenergia.conluz.domain.admin.user.disable;

import org.junit.jupiter.api.DisplayName;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisableUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00.123456789Z");

    @Mock
    private DisableUserRepository disableUserRepository;
    @Mock
    private GetUserRepository getUserRepository;
    @Mock
    private ManagePlatformAdminRepository platformAdminRepository;

    private DisableUserService service() {
        return new DisableUserServiceImpl(disableUserRepository, getUserRepository, platformAdminRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void disable_withNonPlatformAdmin_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(false).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));

        service().disable(id);

        verify(disableUserRepository).disable(eq(id), any(Instant.class));
    }

    @Test
    void disable_withPlatformAdminAndAnotherAdminExists_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).enabled(true).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        when(platformAdminRepository.countPlatformAdmins()).thenReturn(2L);

        service().disable(id);

        verify(disableUserRepository).disable(eq(id), any(Instant.class));
    }

    @Test
    void disable_withLastPlatformAdmin_throwsAndDoesNotDisable() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).enabled(true).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));
        when(platformAdminRepository.countPlatformAdmins()).thenReturn(1L);

        assertThrows(LastPlatformAdminException.class, () -> service().disable(id));

        verify(disableUserRepository, never()).disable(any(), any());
    }

    @Test
    void disable_withUnknownUser_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        when(getUserRepository.findById(id)).thenReturn(Optional.empty());

        service().disable(id);

        verify(disableUserRepository).disable(eq(id), any(Instant.class));
    }

    @Test
    void disable_recordsTheInstantOfTheApplicationClock() {
        UserId id = UserId.of(UUID.randomUUID());
        when(getUserRepository.findById(id)).thenReturn(Optional.empty());

        service().disable(id);

        verify(disableUserRepository).disable(id, NOW);
    }

    @Test
    @DisplayName("ACC-008 ACC-013 a disabled platform admin is disabled while only one platform admin is enabled, without counting")
    void disable_withDisabledPlatformAdmin_isAllowedWhateverTheCount() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(true).enabled(false).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));

        service().disable(id);

        verify(disableUserRepository).disable(eq(id), any(Instant.class));
        verify(platformAdminRepository, never()).countPlatformAdmins();
    }

    @Test
    @DisplayName("ACC-008 ACC-013 a user without the role is disabled without counting the platform admins")
    void disable_withNonPlatformAdmin_doesNotCountThePlatformAdmins() {
        UserId id = UserId.of(UUID.randomUUID());
        User user = new User.Builder().id(id.getId()).isPlatformAdmin(false).enabled(true).build();
        when(getUserRepository.findById(id)).thenReturn(Optional.of(user));

        service().disable(id);

        verify(platformAdminRepository, never()).countPlatformAdmins();
    }
}
