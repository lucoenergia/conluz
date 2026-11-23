package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.FlatPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TaxRatePeriod;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the values a consumer gets from the shipped configuration, so that a change to
 * {@code src/main/resources/application.properties} cannot silently move the reported
 * money. No profile is activated on purpose: with {@code test} active the assertions would
 * describe {@code application-test.properties} instead of what actually ships.
 */
class EstimatedTariffDefaultsTest {

    private static final SupplyId ANY_SUPPLY = SupplyId.of(UUID.randomUUID());

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ResolverConfiguration.class);

    @Test
    void theShippedPriceIsFifteenCentsBeforeTaxes() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            TariffSegment segment = context.getBean(EstimatedSupplyTariffResolver.class)
                    .scheduleFor(ANY_SUPPLY, new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1)))
                    .getSegments()
                    .get(0);

            assertThat(((FlatPlan) segment.getPlan()).getPricePerKwh()).isEqualByComparingTo("0.15");
        });
    }

    @Test
    void theShippedVatPeriodsAndDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            EstimatedTaxRates rates = context.getBean(EstimatedTaxRates.class);

            assertThat(rates.vat().getPeriods()).containsExactly(
                    period("2024-01-01", "2024-03-01", "0.10"),
                    period("2024-03-01", "2024-07-01", "0.21"),
                    period("2024-07-01", "2025-01-01", "0.10"),
                    period("2025-01-01", "2026-03-22", "0.21"),
                    period("2026-03-22", "2026-06-01", "0.10"),
                    period("2026-06-01", null, "0.21"));
            assertThat(rates.vat().getDefaultRate()).isEqualTo(new BigDecimal("0.21"));
        });
    }

    @Test
    void theShippedElectricityTaxPeriodsAndDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            EstimatedTaxRates rates = context.getBean(EstimatedTaxRates.class);

            assertThat(rates.electricityTax().getPeriods()).containsExactly(
                    period("2024-01-01", "2024-04-01", "0.025"),
                    period("2024-04-01", "2024-07-01", "0.038"),
                    period("2024-07-01", "2026-03-22", "0.0511269632"),
                    period("2026-03-22", "2026-06-01", "0.005"),
                    period("2026-06-01", null, "0.0511269632"));
            assertThat(rates.electricityTax().getDefaultRate()).isEqualTo(new BigDecimal("0.0511269632"));
        });
    }

    /**
     * Exact {@code BigDecimal} equality, scale included, through the record's {@code equals}: the
     * shipped values are pinned as written.
     */
    private static TaxRatePeriod period(String from, String to, String rate) {
        return new TaxRatePeriod(LocalDate.parse(from), to == null ? null : LocalDate.parse(to),
                new BigDecimal(rate));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({EstimatedTaxRatesConfiguration.class, EstimatedSupplyTariffResolver.class})
    static class ResolverConfiguration {
    }
}
