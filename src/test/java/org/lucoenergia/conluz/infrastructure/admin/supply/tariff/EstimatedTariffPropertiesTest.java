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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The estimated price and VAT rate are multiplied by metered kWh to produce reported
 * money, so a misconfigured value must stop startup instead of producing a
 * plausible-looking wrong amount. These tests pin that: every rejected value fails the
 * context, and the failure names the property responsible.
 */
class EstimatedTariffPropertiesTest {

    private static final String PREFIX = "conluz.supply.tariff.estimated";
    private static final String PRICE = PREFIX + ".base-eur-per-kwh";
    private static final String VAT = PREFIX + ".vat-rate";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void validValuesAreBound() {
        contextRunner.withPropertyValues(PRICE + "=0.15", VAT + "=0.21").run(context -> {
            assertThat(context).hasNotFailed();
            EstimatedTariffProperties properties = context.getBean(EstimatedTariffProperties.class);
            assertThat(properties.getBaseEurPerKwh()).isEqualByComparingTo("0.15");
            assertThat(properties.getVatRate()).isEqualByComparingTo("0.21");
        });
    }

    @Test
    void aZeroVatRateIsAccepted() {
        contextRunner.withPropertyValues(PRICE + "=0.15", VAT + "=0").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(EstimatedTariffProperties.class).getVatRate())
                    .isEqualByComparingTo(BigDecimal.ZERO);
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
                Arguments.of("both properties absent", "baseEurPerKwh", new String[]{}),
                Arguments.of("price absent", "baseEurPerKwh", new String[]{VAT + "=0"}),
                Arguments.of("vat absent", "vatRate", new String[]{PRICE + "=0.15"}),
                Arguments.of("price not numeric", "base-eur-per-kwh",
                        new String[]{PRICE + "=abc", VAT + "=0"}),
                Arguments.of("vat not numeric", "vat-rate",
                        new String[]{PRICE + "=0.15", VAT + "=abc"}),
                Arguments.of("price is zero", "baseEurPerKwh",
                        new String[]{PRICE + "=0", VAT + "=0"}),
                Arguments.of("price is negative", "baseEurPerKwh",
                        new String[]{PRICE + "=-0.15", VAT + "=0"}),
                Arguments.of("vat is negative", "vatRate",
                        new String[]{PRICE + "=0.15", VAT + "=-0.01"}),
                Arguments.of("vat is exactly one", "vatRate",
                        new String[]{PRICE + "=0.15", VAT + "=1"}),
                Arguments.of("vat is above one", "vatRate",
                        new String[]{PRICE + "=0.15", VAT + "=1.5"})
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
