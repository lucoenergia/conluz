package org.lucoenergia.conluz.infrastructure.shared.emission;

import org.lucoenergia.conluz.domain.shared.emission.EmissionFactors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the electricity emission factors, so that they are bound and validated when the application
 * starts, and hands them to the domain.
 */
@Configuration
@EnableConfigurationProperties(EmissionFactorProperties.class)
public class EmissionFactorConfiguration {

    @Bean
    EmissionFactors emissionFactors(EmissionFactorProperties properties) {
        return EmissionFactors.of(properties.getKgCo2ePerKwh());
    }
}
