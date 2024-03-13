package org.lucoenergia.conluz.domain.admin.supply;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;

import java.util.Random;
import java.util.UUID;

public class SupplyMother {

    public static Supply.Builder random() {
        return random(UserMother.randomUser());
    }

    public static Supply.Builder random(User user) {
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode(RandomStringUtils.random(20, true, true))
                .withAddress(RandomStringUtils.random(20, true, true))
                .withPartitionCoefficient(new Random().nextFloat())
                .withEnabled(new Random().nextBoolean())
                .withUser(user)
                .withName(RandomStringUtils.random(10, true, false));
    }
}
