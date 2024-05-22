package org.lucoenergia.conluz.domain.admin.supply;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

public class SupplyMother {

    public static Supply.Builder random() {
        return random(UserMother.randomUser());
    }

    public static SupplyEntity.Builder randomEntity() {
        return new SupplyEntity.Builder()
                .withId(UUID.randomUUID())
                .withCode(RandomStringUtils.random(20, true, true))
                .withAddress(RandomStringUtils.random(20, true, true))
                .withPartitionCoefficient(new Random().nextFloat())
                .withEnabled(new Random().nextBoolean())
                .withUser(UserMother.randomUserEntity())
                .withName(RandomStringUtils.random(10, true, false))
                .withDatadisDistributor(RandomStringUtils.random(10, true, false))
                .withDatadisDistributorCode(RandomStringUtils.random(1, false, true))
                .withDatadisPointType(new Random().nextInt())
                .withDatadisValidDateFrom(LocalDate.now())
                .withShellyId(RandomStringUtils.random(10, true, true))
                .withShellyMac(RandomStringUtils.random(10, true, true))
                .withShellyMqttPrefix(RandomStringUtils.random(10, true, true));
    }

    public static Supply.Builder random(User user) {
        return new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode(RandomStringUtils.random(20, true, true))
                .withAddress(RandomStringUtils.random(20, true, true))
                .withPartitionCoefficient(new Random().nextFloat())
                .withEnabled(new Random().nextBoolean())
                .withUser(user)
                .withName(RandomStringUtils.random(10, true, false))
                .withDatadisDistributor(RandomStringUtils.random(10, true, false))
                .withDatadisDistributorCode(RandomStringUtils.random(1, false, true))
                .withDatadisPointType(new Random().nextInt())
                .withDatadisValidDateFrom(LocalDate.now())
                .withShellyId(RandomStringUtils.random(10, true, true))
                .withShellyMac(RandomStringUtils.random(10, true, true))
                .withShellyMqttPrefix(RandomStringUtils.random(10, true, true));
    }
}
