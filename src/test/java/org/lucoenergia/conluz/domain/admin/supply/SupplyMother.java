package org.lucoenergia.conluz.domain.admin.supply;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.datadis.SupplyDatadis;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.shelly.SupplyShelly;
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
                .withShelly(new org.lucoenergia.conluz.infrastructure.admin.supply.shelly.SupplyShellyEntity.Builder()
                        .withId(RandomStringUtils.random(10, true, true))
                        .withMacAddress(RandomStringUtils.random(10, true, true))
                        .withMqttPrefix(RandomStringUtils.random(10, true, true))
                        .build())
                .withDatadis(new org.lucoenergia.conluz.infrastructure.admin.supply.datadis.SupplyDatadisEntity.Builder()
                        .withThirdParty(new Random().nextBoolean())
                        .build())
                .withDistributor(new org.lucoenergia.conluz.infrastructure.admin.supply.distributor.SupplyDistributorEntity.Builder()
                        .withName(RandomStringUtils.random(10, true, false))
                        .withCode(RandomStringUtils.random(1, false, true))
                        .withPointType(new Random().nextInt())
                        .build())
                .withContract(new org.lucoenergia.conluz.infrastructure.admin.supply.contract.SupplyContractEntity.Builder()
                        .withValidDateFrom(LocalDate.now())
                        .build());
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
                .withShelly(new SupplyShelly.Builder()
                        .withId(RandomStringUtils.random(10, true, true))
                        .withMacAddress(RandomStringUtils.random(10, true, true))
                        .withMqttPrefix(RandomStringUtils.random(10, true, true))
                        .build())
                .withDatadis(new SupplyDatadis.Builder()
                        .withThirdParty(new Random().nextBoolean())
                        .build())
                .withDistributor(new SupplyDistributor.Builder()
                        .withName(RandomStringUtils.random(10, true, false))
                        .withCode(RandomStringUtils.random(1, false, true))
                        .withPointType(new Random().nextInt())
                        .build())
                .withContract(new SupplyContract.Builder()
                        .withValidDateFrom(LocalDate.now())
                        .build());
    }
}
