package org.lucoenergia.conluz.infrastructure.shared.emission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The emission factors are multiplied by the plants' production, so a missing or negative factor must stop
 * startup instead of producing a plausible-looking wrong figure, and the failure must name the property.
 */
class EmissionFactorPropertiesTest {

    private static final String PREFIX = "conluz.emission-factors.electricity";
    private static final String FACTORS = PREFIX + ".kg-co2e-per-kwh";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(EmissionFactorConfiguration.class);

    @Test
    void factorsAreBoundPerYear() {
        contextRunner.withPropertyValues(FACTORS + "[2024]=0.200", FACTORS + "[2025]=0").run(context -> {
            assertThat(context).hasNotFailed();
            Map<Integer, BigDecimal> factors = context.getBean(EmissionFactorProperties.class).getKgCo2ePerKwh();
            assertThat(factors).containsOnlyKeys(2024, 2025);
            assertThat(factors.get(2024)).isEqualByComparingTo("0.200");
            assertThat(factors.get(2025)).isEqualByComparingTo("0");
        });
    }

    @Test
    @DisplayName("CEM-015 without any factor the application refuses to start, naming the property")
    void withoutAnyFactorStartupFails() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context)).contains(PREFIX).contains("kgCo2ePerKwh");
        });
    }

    @Test
    @DisplayName("CEM-015 with a negative factor the application refuses to start, naming the property")
    void withANegativeFactorStartupFails() {
        contextRunner.withPropertyValues(FACTORS + "[2024]=0.200", FACTORS + "[2025]=-0.150").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failureMessages(context)).contains(PREFIX).contains("kgCo2ePerKwh[2025]");
        });
    }

    @Test
    @DisplayName("CEM-015 the shipped configuration holds the MITECO Mix sin GdO factors from 2021 to 2025")
    void shippedFactorsAreTheMitecoMixSinGdoValues() {
        contextRunner.withInitializer(new ConfigDataApplicationContextInitializer()).run(context -> {
            assertThat(context).hasNotFailed();
            Map<Integer, BigDecimal> factors = context.getBean(EmissionFactorProperties.class).getKgCo2ePerKwh();
            assertThat(factors).containsOnlyKeys(2021, 2022, 2023, 2024, 2025);
            assertThat(factors.get(2021)).isEqualByComparingTo("0.259");
            assertThat(factors.get(2022)).isEqualByComparingTo("0.273");
            assertThat(factors.get(2023)).isEqualByComparingTo("0.260");
            assertThat(factors.get(2024)).isEqualByComparingTo("0.283");
            assertThat(factors.get(2025)).isEqualByComparingTo("0.258");
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
