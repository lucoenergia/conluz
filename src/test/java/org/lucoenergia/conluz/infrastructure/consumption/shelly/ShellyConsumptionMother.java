package org.lucoenergia.conluz.infrastructure.consumption.shelly;

import org.apache.commons.lang3.RandomStringUtils;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;

import java.time.Instant;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ShellyConsumptionMother {

    public static ShellyInstantConsumption.Builder random() {
        return new ShellyInstantConsumption.Builder()
                .withConsumptionKWh(new Random().nextDouble())
                .withChannel(Arrays.asList("0", "1").get(ThreadLocalRandom.current().nextInt(2)))
                .withTimestamp(Instant.now())
                .withPrefix(RandomStringUtils.randomAlphanumeric(10));
    }
}
