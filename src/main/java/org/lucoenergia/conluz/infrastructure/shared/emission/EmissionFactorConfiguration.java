package org.lucoenergia.conluz.infrastructure.shared.emission;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the electricity emission factors, so that they are bound and validated when the application
 * starts.
 */
@Configuration
@EnableConfigurationProperties(EmissionFactorProperties.class)
public class EmissionFactorConfiguration {
}
