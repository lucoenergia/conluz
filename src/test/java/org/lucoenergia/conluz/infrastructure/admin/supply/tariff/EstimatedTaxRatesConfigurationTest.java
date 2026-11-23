package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TaxRatePeriod;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tax rate timeline that breaks TRF-004 must stop the application from starting, and the failure
 * must name the property an operator has to fix. The rules themselves are covered one by one in
 * {@code TaxRatePeriodsTest}; these tests cover the translation into a property, for a representative
 * subset of them.
 */
class EstimatedTaxRatesConfigurationTest {

    private static final String TAXES = "conluz.supply.tariff.estimated.taxes.";
    private static final String VAT = TAXES + "vat";
    private static final String ELECTRICITY_TAX = TAXES + "electricity-tax";

    private static final String[] VALID = {
            "conluz.supply.tariff.estimated.base-eur-per-kwh=0.15",
            TAXES + "default-vat-rate=0.21",
            TAXES + "default-electricity-tax-rate=0.0511269632",
            VAT + "[0].from=2024-01-01", VAT + "[0].to=2024-03-01", VAT + "[0].rate=0.10",
            VAT + "[1].from=2024-03-01", VAT + "[1].rate=0.21",
            ELECTRICITY_TAX + "[0].from=2024-01-01", ELECTRICITY_TAX + "[0].to=2024-04-01",
            ELECTRICITY_TAX + "[0].rate=0.025",
            ELECTRICITY_TAX + "[1].from=2024-04-01", ELECTRICITY_TAX + "[1].rate=0.038"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EstimatedTaxRatesConfiguration.class);

    @Test
    void aValidConfigurationBuildsBothTimelines() {
        contextRunner.withPropertyValues(VALID).run(context -> {
            assertThat(context).hasNotFailed();
            EstimatedTaxRates rates = context.getBean(EstimatedTaxRates.class);

            assertThat(rates.vat().getPeriods()).containsExactly(
                    new TaxRatePeriod(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-03-01"),
                            new BigDecimal("0.10")),
                    new TaxRatePeriod(LocalDate.parse("2024-03-01"), null, new BigDecimal("0.21")));
            assertThat(rates.vat().getDefaultRate()).isEqualByComparingTo("0.21");
            assertThat(rates.electricityTax().getPeriods()).containsExactly(
                    new TaxRatePeriod(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-04-01"),
                            new BigDecimal("0.025")),
                    new TaxRatePeriod(LocalDate.parse("2024-04-01"), null, new BigDecimal("0.038")));
            assertThat(rates.electricityTax().getDefaultRate()).isEqualByComparingTo("0.0511269632");
        });
    }

    @ParameterizedTest(name = "TRF-004 {0} stops startup naming {1}")
    @MethodSource("rejectedConfigurations")
    void anInvalidTimelineStopsStartupNamingTheProperty(String label, String expectedProperty,
                                                         List<String> overrides) {
        contextRunner.withPropertyValues(VALID).withPropertyValues(overrides.toArray(String[]::new))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(failureMessages(context)).contains(expectedProperty);
                });
    }

    private static Stream<Arguments> rejectedConfigurations() {
        return Stream.of(
                Arguments.of("overlapping periods", VAT + "[1].from",
                        List.of(VAT + "[1].from=2024-02-15")),
                Arguments.of("a gap between periods", ELECTRICITY_TAX + "[1].from",
                        List.of(ELECTRICITY_TAX + "[1].from=2024-05-01")),
                Arguments.of("unordered periods", VAT + "[1].from",
                        List.of(VAT + "[1].from=2023-06-01")),
                Arguments.of("an open-ended period that is not the last", VAT + "[0].to",
                        List.of(VAT + "[0].to=")),
                Arguments.of("a rate of exactly one", VAT + "[1].rate",
                        List.of(VAT + "[1].rate=1")),
                Arguments.of("a negative rate", ELECTRICITY_TAX + "[0].rate",
                        List.of(ELECTRICITY_TAX + "[0].rate=-0.025")),
                Arguments.of("a default rate of 21 written as a percentage", TAXES + "default-vat-rate",
                        List.of(TAXES + "default-vat-rate=21"))
        );
    }

    @Test
    @DisplayName("TRF-004 a missing default rate stops startup naming its property")
    void aMissingDefaultRateStopsStartup() {
        String[] withoutDefault = Stream.of(VALID)
                .filter(property -> !property.startsWith(TAXES + "default-electricity-tax-rate"))
                .toArray(String[]::new);

        contextRunner.withPropertyValues(withoutDefault).run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context)).contains(TAXES + "default-electricity-tax-rate");
        });
    }

    private static String failureMessages(AssertableApplicationContext context) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = context.getStartupFailure(); cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append(System.lineSeparator());
        }
        return messages.toString();
    }
}
