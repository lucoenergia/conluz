package org.lucoenergia.conluz.infrastructure.shared.emission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.emission.EmissionFactorFreshness;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the shipped emission factors against today's date, so an outdated factor does not go unnoticed.
 *
 * <p>It depends on the date, so it is tagged out of the regular build, where a date must never break an
 * unrelated change, and run by the {@code emissionFactorFreshness} Gradle task on a monthly schedule
 * ({@code .github/workflows/emission-factors-freshness.yml}). The factors are bound from the shipped
 * {@code application.properties} without starting the application, so it needs no database.
 */
@Tag("emission-factor-freshness")
class EmissionFactorFreshnessCheckTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(EmissionFactorConfiguration.class);

    @Test
    @DisplayName("CEM-016 the shipped emission factors include the year MITECO has published by today")
    void theShippedFactorsAreUpToDate() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            EmissionFactorFreshness.Result result = EmissionFactorFreshness.check(
                    context.getBean(EmissionFactorProperties.class).getKgCo2ePerKwh().keySet(),
                    LocalDate.now(MADRID));

            assertThat(result.configured())
                    .withFailMessage("The emission factor for %d is missing from "
                                    + "conluz.emission-factors.electricity.kg-co2e-per-kwh in application.properties. "
                                    + "Update it from the latest MITECO \"Factores de emisión\" document (sheet "
                                    + "\"5. Factores de mix eléctricos\", row \"Mix sin GdO\"), together with every "
                                    + "earlier year the new version revises, and redeploy.",
                            result.requiredYear())
                    .isTrue();
        });
    }
}
