package org.lucoenergia.conluz.domain.admin.supply;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SupplyTariffTest {

    @Test
    void shouldThrowExceptionWhenSupplyIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SupplyTariff.Builder()
                        .withId(UUID.randomUUID())
                        .withSupply(null)
                        .build()
        );
        assertEquals("Supply tariff must be associated with a supply", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenTariffValuesAreNull() {
        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SupplyTariff.Builder()
                        .withId(UUID.randomUUID())
                        .withSupply(supply)
                        .withValley(null)
                        .withPeak(null)
                        .withOffPeak(null)
                        .build()
        );
        assertEquals("Supply tariff must have all tariff values", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenTariffValuesAreNegative() {
        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new SupplyTariff.Builder()
                        .withId(UUID.randomUUID())
                        .withSupply(supply)
                        .withValley(-1.0)
                        .withPeak(-1.0)
                        .withOffPeak(-1.0)
                        .build()
        );
        assertEquals("Supply tariff tariff values must be positive", exception.getMessage());
    }
}