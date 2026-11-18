package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Binds the configuration of the observed sharing analysis (#579) at startup.
 */
@ConfigurationProperties(prefix = "conluz.sharing-observation")
@Validated
public class SharingObservationProperties {

    /**
     * N: the consecutive valid days a run must last to be observed, and an anomaly to be reported. At least 1.
     */
    @Min(1)
    private final int consecutiveDays;

    public SharingObservationProperties(@DefaultValue("3") int consecutiveDays) {
        this.consecutiveDays = consecutiveDays;
    }

    public int getConsecutiveDays() {
        return consecutiveDays;
    }
}
