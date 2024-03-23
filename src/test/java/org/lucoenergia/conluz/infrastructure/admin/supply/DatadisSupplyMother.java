package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.domain.admin.datadis.DistributorCode;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;

import java.util.Random;

public class DatadisSupplyMother {

    public static DatadisSupply.Builder random(String cups) {
        return new DatadisSupply.Builder()
                .withCups(cups)
                .withAddress(RandomStringUtils.random(20))
                .withPostalCode(RandomStringUtils.randomNumeric(5))
                .withProvince(RandomStringUtils.random(10))
                .withMunicipality(RandomStringUtils.random(10))
                .withValidDateFrom(String.format("2024/0%s/1%s", new Random().nextInt(9),
                        new Random().nextInt(9)))
                .withValidDateTo(String.format("2024/0%s/1%s", new Random().nextInt(9),
                        new Random().nextInt(9)))
                .withPointType(5)
                .withDistributor(RandomStringUtils.random(10))
                .withDistributorCode(DistributorCode.E_DISTRIBUCION);
    }
}
