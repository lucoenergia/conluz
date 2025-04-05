package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

public class SupplyEntityMother {

    public static SupplyEntity random() {
        return random(UserMother.randomUserEntity());
    }

    public static SupplyEntity random(UserEntity user) {
        return new SupplyEntity.Builder()
                .withId(UUID.randomUUID())
                .withCode(RandomStringUtils.random(20, true, true))
                .withAddress(RandomStringUtils.random(20, true, true))
                .withPartitionCoefficient(new Random().nextFloat())
                .withEnabled(new Random().nextBoolean())
                .withUser(user)
                .withName(RandomStringUtils.random(10, true, false))

                .withDistributor("EDISTRIBUCION")
                .withDistributorCode("2")
                .withPointType(5)
                .withValidDateFrom(LocalDate.now())
                .withThirdParty(new Random().nextBoolean())

                .withShellyId(RandomStringUtils.random(20, true, true))
                .withShellyMac(RandomStringUtils.random(10, true, true))
                .withShellyMqttPrefix(RandomStringUtils.random(20, true, true))
                .build();
    }
}
