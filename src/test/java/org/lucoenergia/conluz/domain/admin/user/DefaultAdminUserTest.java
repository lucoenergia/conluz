package org.lucoenergia.conluz.domain.admin.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultAdminUserTest {

    @Test
    void numberMustBeAlwaysZero() {
        DefaultAdminUser user = new DefaultAdminUser();

        Assertions.assertEquals(0, user.getNumber());

        user.setNumber(1);

        Assertions.assertEquals(0, user.getNumber());
    }

    @Test
    void mustAlwaysBePlatformAdmin() {
        DefaultAdminUser user = new DefaultAdminUser();

        Assertions.assertTrue(user.isPlatformAdmin());
    }
}
