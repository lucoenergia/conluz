package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.ManagePlatformAdminAccessImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagePlatformAdminAccessTest {

    @Mock
    private ManagePlatformAdminRepository repository;

    private ManagePlatformAdminAccess service() {
        return new ManagePlatformAdminAccessImpl(repository);
    }

    @Test
    void grant_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());

        service().grant(id);

        verify(repository).grant(id);
    }

    @Test
    void revoke_withMoreThanOnePlatformAdmin_delegatesToRepository() {
        UserId id = UserId.of(UUID.randomUUID());
        when(repository.countPlatformAdmins()).thenReturn(2L);

        service().revoke(id);

        verify(repository).revoke(id);
    }

    @Test
    void revoke_withSinglePlatformAdmin_throwsAndDoesNotRevoke() {
        UserId id = UserId.of(UUID.randomUUID());
        when(repository.countPlatformAdmins()).thenReturn(1L);

        assertThrows(LastPlatformAdminException.class, () -> service().revoke(id));

        verify(repository, never()).revoke(any());
    }
}
