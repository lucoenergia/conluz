package org.lucoenergia.conluz.domain.production.plant;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.InverterProvider;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

public class PlantMother {

    public static Plant.Builder random() {
        return random(UserMother.randomUser());
    }

    public static Plant.Builder random(User user) {
        return new Plant.Builder()
                .withId(UUID.randomUUID())
                .withCode(RandomStringUtils.random(20, true, true))
                .withName(RandomStringUtils.random(10, true, false))
                .withDescription(RandomStringUtils.random(30, true, false))
                .withTotalPower(new Random().nextDouble())
                .withInverterProvider(InverterProvider.HUAWEI)
                .withConnectionDate(LocalDate.now())
                .withAddress(RandomStringUtils.random(20, true, true))
                .withUser(user);
    }
}
