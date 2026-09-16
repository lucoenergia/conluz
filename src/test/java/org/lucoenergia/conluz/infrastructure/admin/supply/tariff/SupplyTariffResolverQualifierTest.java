package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyTariffResolver;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSchedule;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the reason {@link EstimatedSupplyTariffResolver} carries a {@code @Qualifier}: a
 * second {@link SupplyTariffResolver} implementation must be addable without making every
 * existing injection point ambiguous.
 */
class SupplyTariffResolverQualifierTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TwoResolversConfiguration.class);

    @Test
    void twoResolverImplementationsCoexistAndTheEstimatedOneIsInjectableByQualifier() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(SupplyTariffResolver.class)).hasSize(2);
            assertThat(context.getBean(TariffConsumer.class).resolver())
                    .isInstanceOf(EstimatedSupplyTariffResolver.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoResolversConfiguration {

        /**
         * Deliberately registered under a bean name that does NOT match the qualifier string,
         * so the injection point below can only be satisfied through the class-level
         * {@code @Qualifier} -- not through Spring's bean-name fallback. Remove the annotation
         * from the resolver and this test fails, which is the point of having it.
         */
        @Bean
        EstimatedSupplyTariffResolver resolverUnderANonMatchingBeanName() {
            return new EstimatedSupplyTariffResolver(new BigDecimal("0.15"), BigDecimal.ZERO);
        }

        /**
         * Stands in for the future real-tariff implementation. It only has to exist and
         * implement the port; it is never invoked.
         */
        @Bean
        SupplyTariffResolver secondSupplyTariffResolver() {
            return new SupplyTariffResolver() {
                @Override
                public TariffSchedule scheduleFor(SupplyId supply, DateRange range) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        TariffConsumer tariffConsumer(
                @Qualifier("estimatedSupplyTariffResolver") SupplyTariffResolver resolver) {
            return new TariffConsumer(resolver);
        }
    }

    record TariffConsumer(SupplyTariffResolver resolver) {
    }
}
