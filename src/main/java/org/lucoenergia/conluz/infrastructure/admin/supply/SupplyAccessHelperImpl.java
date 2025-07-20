package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyAccessHelper;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.springframework.stereotype.Component;

@Component
public class SupplyAccessHelperImpl implements SupplyAccessHelper {

    private final AuthService authService;

    public SupplyAccessHelperImpl(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Check if the current user is an admin or the owner of the supply
     *
     * @param supply the supply to check
     * @return true if the current user is an admin or the owner of the supply
     */
    @Override
    public boolean isAdminOrSupplyOwner(Supply supply) {
        User user = authService.getCurrentUser();

        return user != null && (
                user.getRole() == Role.ADMIN ||
                (supply.getUser() != null &&
                        supply.getUser().getId() != null &&
                        supply.getUser().getId().equals(user.getId()))
        );
    }
}
