package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastPlatformAdminRuleTest {

    @Test
    @DisplayName("ACC-013 the last enabled platform admin blocks the revocation")
    void revocationOfTheLastEnabledPlatformAdminIsBlocked() {
        assertTrue(LastPlatformAdminRule.blocksRevocation(user(true, true), 1));
    }

    @Test
    @DisplayName("ACC-013 an enabled platform admin blocks the revocation with no enabled platform admin counted")
    void revocationIsBlockedWithNoEnabledPlatformAdminCounted() {
        assertTrue(LastPlatformAdminRule.blocksRevocation(user(true, true), 0));
    }

    @Test
    @DisplayName("ACC-013 an enabled platform admin may be revoked while another one is enabled")
    void revocationOfAnEnabledPlatformAdminIsAllowedWithTwoEnabled() {
        assertFalse(LastPlatformAdminRule.blocksRevocation(user(true, true), 2));
    }

    @Test
    @DisplayName("ACC-013 a disabled platform admin may be revoked while only one platform admin is enabled")
    void revocationOfADisabledPlatformAdminIsAllowedWithOneEnabled() {
        assertFalse(LastPlatformAdminRule.blocksRevocation(user(true, false), 1));
    }

    @Test
    @DisplayName("ACC-013 a user without the role does not count towards the rule")
    void aUserWithoutTheRoleNeverBlocks() {
        assertFalse(LastPlatformAdminRule.blocksRevocation(user(false, true), 1));
    }

    private User user(boolean platformAdmin, boolean enabled) {
        User user = UserMother.randomUser();
        user.setPlatformAdmin(platformAdmin);
        user.setEnabled(enabled);
        return user;
    }
}
