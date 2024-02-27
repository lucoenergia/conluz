package org.lucoenergia.conluz.domain.consumption.datadis;

import org.apache.commons.lang3.RandomStringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class ConsumptionMother {

    public static Consumption random() {
        Consumption consumption = new Consumption();
        consumption.setCups(RandomStringUtils.random(20, true, true));
        consumption.setDate(randomDate());
        consumption.setTime(randomTime());
        consumption.setConsumptionKWh(new Random().nextFloat());
        consumption.setObtainMethod("Real");
        consumption.setSurplusEnergyKWh(new Random().nextFloat());
        return consumption;
    }

    public static String randomDate() {
        Random random = new Random();
        int minDay = (int) YearMonth.of(2023, 1).atDay(1).toEpochDay();
        int maxDay = (int) YearMonth.now().atEndOfMonth().toEpochDay();
        long randomDay = minDay + random.nextInt(maxDay - minDay);

        YearMonth randomMonth = YearMonth.from(LocalDate.ofEpochDay(randomDay));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM");
        return randomMonth.format(formatter);
    }

    public static String randomTime() {
        Random random = new Random();
        int randomHour = random.nextInt(24);
        int randomMinute = random.nextInt(60);
        LocalTime randomTime = LocalTime.of(randomHour, randomMinute);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm");
        return randomTime.format(formatter);
    }
}
