package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PlatformAdmin;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPlatformAdminsServiceTest {

    @Mock
    private GetPlatformAdminsRepository repository;

    private GetPlatformAdminsService service() {
        return new GetPlatformAdminsServiceImpl(repository);
    }

    @Test
    @DisplayName("ACC-011 the platform admins are returned in the order the repository reads them")
    void thePlatformAdminsAreReturnedInOrder() {
        User first = admin(true);
        User second = admin(false);
        when(repository.findAll()).thenReturn(List.of(first, second));

        List<PlatformAdmin> admins = service().findAll();

        assertEquals(2, admins.size());
        assertSame(first, admins.get(0).user());
        assertSame(second, admins.get(1).user());
    }

    @Test
    @DisplayName("ACC-011 while only one platform admin is enabled, only that one is not revocable")
    void onlyTheLastEnabledAdminIsNotRevocable() {
        User enabled = admin(true);
        User disabled = admin(false);
        User otherDisabled = admin(false);
        when(repository.findAll()).thenReturn(List.of(disabled, enabled, otherDisabled));

        List<PlatformAdmin> admins = service().findAll();

        assertTrue(admins.get(0).revocable());
        assertFalse(admins.get(1).revocable());
        assertTrue(admins.get(2).revocable());
    }

    @Test
    @DisplayName("ACC-011 every platform admin is revocable while two or more of them are enabled")
    void everyOneIsRevocableWithTwoEnabledAdmins() {
        when(repository.findAll()).thenReturn(List.of(admin(true), admin(false), admin(true)));

        List<PlatformAdmin> admins = service().findAll();

        admins.forEach(admin -> assertTrue(admin.revocable()));
    }

    private User admin(boolean enabled) {
        User user = UserMother.randomUser();
        user.setEnabled(enabled);
        user.setPlatformAdmin(true);
        return user;
    }
}
