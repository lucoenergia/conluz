package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;

public class SupplyMother {

    public static Supply random(User user) {
        return new Supply(RandomStringUtils.random(20, true, true), user,
                RandomStringUtils.random(10, true, false),
                RandomStringUtils.random(20, true, true),
                RandomUtils.nextFloat(), RandomUtils.nextBoolean());
    }
}
