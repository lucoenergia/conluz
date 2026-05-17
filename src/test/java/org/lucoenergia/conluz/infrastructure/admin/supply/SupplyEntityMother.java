package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.contract.SupplyContractEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.datadis.SupplyDatadisEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.distributor.SupplyDistributorEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.shelly.SupplyShellyEntity;
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
                .withDistributor(new SupplyDistributorEntity.Builder()
                        .withName("EDISTRIBUCION")
                        .withCode("2")
                        .withPointType(5)
                        .build())
                .withContract(new SupplyContractEntity.Builder()
                        .withValidDateFrom(LocalDate.now())
                        .build())
                .withDatadis(new SupplyDatadisEntity.Builder()
                        .withThirdParty(new Random().nextBoolean())
                        .build())
                .withShelly(new SupplyShellyEntity.Builder()
                        .withId(RandomStringUtils.random(20, true, true))
                        .withMacAddress(RandomStringUtils.random(10, true, true))
                        .withMqttPrefix(RandomStringUtils.random(20, true, true))
                        .build())
                .build();
    }
}
