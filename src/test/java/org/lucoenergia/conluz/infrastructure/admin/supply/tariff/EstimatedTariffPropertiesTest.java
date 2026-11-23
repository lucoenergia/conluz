package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The estimated price is multiplied by metered kWh to produce reported money, so a
 * misconfigured value must stop startup instead of producing a plausible-looking wrong
 * amount. These tests pin that: every rejected value fails the context, and the failure
 * names the property responsible. They also pin how the tax periods are bound; the rules
 * over them are checked by {@link EstimatedTaxRatesConfigurationTest}.
 */
class EstimatedTariffPropertiesTest {

    private static final String PREFIX = "conluz.supply.tariff.estimated";
    private static final String PRICE = PREFIX + ".base-eur-per-kwh";
    private static final String VAT = PREFIX + ".taxes.vat";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void validValuesAreBound() {
        contextRunner.withPropertyValues(PRICE + "=0.15",
                PREFIX + ".taxes.default-vat-rate=0.21",
                PREFIX + ".taxes.default-electricity-tax-rate=0.0511269632",
                VAT + "[0].from=2024-01-01", VAT + "[0].to=2024-03-01", VAT + "[0].rate=0.10",
                VAT + "[1].from=2024-03-01", VAT + "[1].rate=0.21",
                PREFIX + ".taxes.electricity-tax[0].from=2024-01-01",
                PREFIX + ".taxes.electricity-tax[0].rate=0.025").run(context -> {
            assertThat(context).hasNotFailed();
            EstimatedTariffProperties properties = context.getBean(EstimatedTariffProperties.class);
            assertThat(properties.getBaseEurPerKwh()).isEqualByComparingTo("0.15");
            EstimatedTariffProperties.Taxes taxes = properties.getTaxes();
            assertThat(taxes.defaultVatRate()).isEqualByComparingTo("0.21");
            assertThat(taxes.defaultElectricityTaxRate()).isEqualByComparingTo("0.0511269632");
            assertThat(taxes.vat()).containsExactly(
                    new EstimatedTariffProperties.Period(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-03-01"),
                            new BigDecimal("0.10")),
                    new EstimatedTariffProperties.Period(LocalDate.parse("2024-03-01"), null,
                            new BigDecimal("0.21")));
            assertThat(taxes.electricityTax()).containsExactly(
                    new EstimatedTariffProperties.Period(LocalDate.parse("2024-01-01"), null,
                            new BigDecimal("0.025")));
        });
    }

    @Test
    void withNoTaxConfiguredTheListsAreEmptyAndTheDefaultsAbsent() {
        contextRunner.withPropertyValues(PRICE + "=0.15").run(context -> {
            assertThat(context).hasNotFailed();
            EstimatedTariffProperties.Taxes taxes = context.getBean(EstimatedTariffProperties.class).getTaxes();
            assertThat(taxes.vat()).isEmpty();
            assertThat(taxes.electricityTax()).isEmpty();
            assertThat(taxes.defaultVatRate()).isNull();
            assertThat(taxes.defaultElectricityTaxRate()).isNull();
        });
    }

    /**
     * {@code expectedToken} differs in spelling by failure kind, and that is not an
     * oversight: a validation failure reports the Java field ({@code baseEurPerKwh}) while a
     * conversion failure reports the configuration key ({@code base-eur-per-kwh}). Both name
     * the property, which is what the requirement asks for. The constraint's own default
     * message is locale-dependent and is deliberately not asserted on.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedConfigurations")
    void rejectedConfigurationFailsStartupNamingTheProperty(String label, String expectedToken,
                                                            String[] properties) {
        contextRunner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context)).contains(PREFIX).contains(expectedToken);
        });
    }

    private static Stream<Arguments> rejectedConfigurations() {
        return Stream.of(
                Arguments.of("price absent", "baseEurPerKwh", new String[]{}),
                Arguments.of("price not numeric", "base-eur-per-kwh", new String[]{PRICE + "=abc"}),
                Arguments.of("price is zero", "baseEurPerKwh", new String[]{PRICE + "=0"}),
                Arguments.of("price is negative", "baseEurPerKwh", new String[]{PRICE + "=-0.15"}),
                Arguments.of("period start not a date", "from",
                        new String[]{PRICE + "=0.15", VAT + "[0].from=01/01/2024", VAT + "[0].rate=0.21"}),
                Arguments.of("period rate not numeric", "rate",
                        new String[]{PRICE + "=0.15", VAT + "[0].from=2024-01-01", VAT + "[0].rate=abc"})
        );
    }

    private static String failureMessages(AssertableApplicationContext context) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = context.getStartupFailure(); cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append(System.lineSeparator());
        }
        return messages.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EstimatedTariffProperties.class)
    static class PropertiesConfiguration {
    }
}
