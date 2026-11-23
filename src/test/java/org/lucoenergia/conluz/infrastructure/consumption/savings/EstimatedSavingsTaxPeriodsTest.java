package org.lucoenergia.conluz.infrastructure.consumption.savings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.consumption.EstimatedPrice;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.GetDatadisConsumptionAggregateRepository;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.EstimatedSupplyTariffResolver;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.EstimatedTariffProperties;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.EstimatedTaxRatesConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Savings priced end to end with the taxes shipped in {@code application.properties}: the estimated
 * resolver built from that configuration, the real calculator, and an in-memory stand-in for the hourly
 * self-consumption store. No profile is activated, so the shipped periods apply rather than the test
 * profile's zero-rate ones.
 *
 * <p>Expected amounts are computed from the rules -- {@code base * (1 + electricity tax) * (1 + VAT)},
 * with the rates in force on the civil date in Europe/Madrid -- never read off the implementation.
 */
class EstimatedSavingsTaxPeriodsTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final BigDecimal BASE = new BigDecimal("0.15");
    /**
     * VAT 0.21 and electricity tax 0.0511269632, in force until 2026-03-22.
     */
    private static final BigDecimal BEFORE_MARCH_22 =
            BASE.multiply(new BigDecimal("1.0511269632")).multiply(new BigDecimal("1.21"));
    /**
     * VAT 0.10 and electricity tax 0.005, in force from 2026-03-22.
     */
    private static final BigDecimal FROM_MARCH_22 =
            BASE.multiply(new BigDecimal("1.005")).multiply(new BigDecimal("1.10"));

    private final GetDatadisConsumptionAggregateRepository aggregateRepository =
            mock(GetDatadisConsumptionAggregateRepository.class);
    private final ZoneResolver zoneResolver = mock(ZoneResolver.class);
    private final Supply supply = SupplyMother.random().build();

    /**
     * The self-consumption of each hour of March 2026, keyed by the hour's start. Every value is a
     * multiple of 0.25, so any order of summing it is exact in a double and the comparisons below can
     * be exact too.
     */
    private final NavigableMap<Instant, Double> hourlyKWh = new TreeMap<>();

    private SupplySavingsCalculator calculator;

    @BeforeEach
    void setUp() {
        when(zoneResolver.resolveZoneIdForSupply(supply.getId())).thenReturn(MADRID);

        Instant hour = march(1).toInstant();
        int index = 0;
        while (hour.isBefore(march(32).toInstant())) {
            hourlyKWh.put(hour, 0.5 + (index % 8) * 0.25);
            hour = hour.plus(Duration.ofHours(1));
            index++;
        }
        when(aggregateRepository.sumSelfConsumptionKWh(eq(supply), any(Instant.class), any(Instant.class)))
                .thenAnswer(invocation -> sum(invocation.getArgument(1), invocation.getArgument(2)));

        AtomicReference<SupplySavingsCalculator> built = new AtomicReference<>();
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ShippedTariffConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    built.set(new SupplySavingsCalculatorImpl(aggregateRepository,
                            context.getBean(EstimatedSupplyTariffResolver.class), zoneResolver,
                            context.getBean(EstimatedTariffProperties.class)));
                });
        calculator = built.get();
    }

    @Test
    @DisplayName("TRF-001 10 kWh self-consumed on 2024-02-15 are worth 10 x 0.15 x 1.025 x 1.10, unrounded")
    void aDayIsPricedWithTheShippedTaxesOfItsDate() {
        SupplySavings savings = calculator.estimate(supply,
                ZonedDateTime.of(2024, 2, 15, 0, 0, 0, 0, MADRID).toInstant(),
                ZonedDateTime.of(2024, 2, 16, 0, 0, 0, 0, MADRID).toInstant(), 10d);

        assertEquals(0, new BigDecimal("1.69125").compareTo(savings.getAmountEur()),
                () -> "amount was " + savings.getAmountEur());
    }

    @Test
    @DisplayName("TRF-005 a figure priced with taxes still reports the 0.15 energy-term price before taxes")
    void theEstimatedPriceStaysTheBaseBeforeTaxes() {
        SupplySavings savings = calculator.estimate(supply,
                ZonedDateTime.of(2024, 2, 15, 0, 0, 0, 0, MADRID).toInstant(),
                ZonedDateTime.of(2024, 2, 16, 0, 0, 0, 0, MADRID).toInstant(), 10d);

        assertEquals(EstimatedPrice.of(new BigDecimal("0.15")), savings.getEstimatedPrice());
    }

    /**
     * March 2026 crosses the 2026-03-22 change of both taxes and the 2026-03-29 switch to summer time,
     * a day of 23 hours. The month, its days and its hours must agree to the last decimal (EVL-002),
     * and the month must split at the civil midnight in Madrid: the hour from 00:00 to 01:00 on the
     * 22nd is still the 21st in UTC.
     */
    @Test
    @DisplayName("TRF-002 a month crossing a tax change equals the sum of its days and of its hours")
    void aMonthCrossingATaxChangeEqualsTheSumOfItsDaysAndHours() {
        SupplySavings month = calculator.estimate(supply, march(1).toInstant(), march(32).toInstant());

        BigDecimal days = BigDecimal.ZERO;
        for (int day = 1; day <= 31; day++) {
            days = days.add(calculator.estimate(supply, march(day).toInstant(), march(day + 1).toInstant())
                    .getAmountEur());
        }
        BigDecimal hours = BigDecimal.ZERO;
        for (Instant hour : hourlyKWh.keySet()) {
            hours = hours.add(calculator.estimate(supply, hour, hour.plus(Duration.ofHours(1))).getAmountEur());
        }

        BigDecimal expected = BigDecimal.valueOf(sum(march(1).toInstant(), march(22).toInstant()))
                .multiply(BEFORE_MARCH_22)
                .add(BigDecimal.valueOf(sum(march(22).toInstant(), march(32).toInstant())).multiply(FROM_MARCH_22));
        assertEquals(0, expected.compareTo(month.getAmountEur()), () -> "month was " + month.getAmountEur());
        assertEquals(0, month.getAmountEur().compareTo(days), "days summed to " + days);
        assertEquals(0, month.getAmountEur().compareTo(hours), "hours summed to " + hours);
        assertEquals(743, hourlyKWh.size(), "March 2026 has one hour less in Madrid");
    }

    /**
     * The civil midnight starting {@code day} of March 2026 in Madrid; day 32 is 1 April.
     */
    private static ZonedDateTime march(int day) {
        return LocalDate.of(2026, 3, 1).plusDays(day - 1).atStartOfDay(MADRID);
    }

    private double sum(Instant from, Instant toExclusive) {
        return hourlyKWh.subMap(from, true, toExclusive, false).values().stream()
                .mapToDouble(Double::doubleValue).sum();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({EstimatedTaxRatesConfiguration.class, EstimatedSupplyTariffResolver.class})
    static class ShippedTariffConfiguration {
    }
}
