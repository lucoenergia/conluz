package org.lucoenergia.conluz.infrastructure.shared.emission;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Binds and validates the electricity emission factors at startup: one factor per calendar year, in kg
 * CO₂e/kWh.
 *
 * <p>The factors are multiplied by the energy the community's plants produced, so a missing or negative
 * factor must stop the application from starting rather than show a plausible-looking wrong figure.
 * Constructor binding plus {@code @Validated} turns each of those into a startup failure naming the
 * property.
 */
@ConfigurationProperties(prefix = "conluz.emission-factors.electricity")
@Validated
public class EmissionFactorProperties {

    /**
     * The factor of each calendar year, keyed by the year. At least one is required, and none may be
     * negative.
     */
    @NotEmpty
    private final Map<@NotNull Integer, @NotNull @DecimalMin("0") BigDecimal> kgCo2ePerKwh;

    public EmissionFactorProperties(Map<Integer, BigDecimal> kgCo2ePerKwh) {
        this.kgCo2ePerKwh = kgCo2ePerKwh;
    }

    public Map<Integer, BigDecimal> getKgCo2ePerKwh() {
        return kgCo2ePerKwh;
    }
}
