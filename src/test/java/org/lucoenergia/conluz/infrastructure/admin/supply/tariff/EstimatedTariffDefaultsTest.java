package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.FlatPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment;
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
    void theShippedDefaultsArePricedAtFifteenCentsWithoutVat() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            TariffSegment segment = context.getBean(EstimatedSupplyTariffResolver.class)
                    .scheduleFor(ANY_SUPPLY, new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)))
                    .getSegments()
                    .get(0);

            assertThat(((FlatPlan) segment.getPlan()).getPricePerKwh()).isEqualByComparingTo("0.15");
            assertThat(segment.getVatRate()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(EstimatedSupplyTariffResolver.class)
    static class ResolverConfiguration {
    }
}
