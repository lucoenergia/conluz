package org.lucoenergia.conluz.domain.admin.user.platformadmin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastPlatformAdminRuleTest {

    @Test
    void revocationIsBlockedWithNoEnabledPlatformAdmin() {
        assertTrue(LastPlatformAdminRule.blocksRevocation(0));
    }

    @Test
    void revocationIsBlockedWithOneEnabledPlatformAdmin() {
        assertTrue(LastPlatformAdminRule.blocksRevocation(1));
    }

    @Test
    void revocationIsAllowedWithTwoEnabledPlatformAdmins() {
        assertFalse(LastPlatformAdminRule.blocksRevocation(2));
    }
}
