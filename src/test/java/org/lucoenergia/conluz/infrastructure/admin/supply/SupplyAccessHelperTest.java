package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyAccessHelper;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SupplyAccessHelperTest {

    private final AuthService authService = Mockito.mock(AuthService.class);

    private final SupplyAccessHelper supplyAccessHelper = new SupplyAccessHelperImpl(authService);

    @Test
    void shouldReturnTrueWhenUserIsAdmin() {
        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setRole(Role.ADMIN);

        when(authService.getCurrentUser()).thenReturn(adminUser);

        Supply supply = createTestSupply(UUID.randomUUID());

        boolean result = supplyAccessHelper.isAdminOrSupplyOwner(supply);

        assertTrue(result);
    }

    @Test
    void shouldReturnTrueWhenUserIsSupplyOwner() {
        UUID userId = UUID.randomUUID();

        User ownerUser = new User();
        ownerUser.setId(userId);
        ownerUser.setRole(Role.PARTNER);

        when(authService.getCurrentUser()).thenReturn(ownerUser);

        Supply supply = createTestSupply(userId);

        boolean result = supplyAccessHelper.isAdminOrSupplyOwner(supply);

        assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenUserIsNotSupplyOwner() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.PARTNER);

        when(authService.getCurrentUser()).thenReturn(user);

        Supply supply = createTestSupply(UUID.randomUUID());

        boolean result = supplyAccessHelper.isAdminOrSupplyOwner(supply);

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSupplyHasNoUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.PARTNER);

        when(authService.getCurrentUser()).thenReturn(user);

        Supply supply = SupplyMother.random()
                .withUser(null)
                .build();

        boolean result = supplyAccessHelper.isAdminOrSupplyOwner(supply);

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenNoCurrentUser() {
        when(authService.getCurrentUser()).thenReturn(null);

        Supply supply = createTestSupply(UUID.randomUUID());

        boolean result = supplyAccessHelper.isAdminOrSupplyOwner(supply);

        assertFalse(result);
    }

    private Supply createTestSupply(UUID userId) {
        User supplyOwner = new User();
        supplyOwner.setId(userId);
        supplyOwner.setRole(Role.PARTNER);

        return SupplyMother.random()
                .withUser(supplyOwner)
                .build();
    }
}