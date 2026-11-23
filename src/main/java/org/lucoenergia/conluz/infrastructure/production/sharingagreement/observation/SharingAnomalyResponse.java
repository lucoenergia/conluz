package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAnomaly;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAnomalyType;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A sustained disagreement between what the distributor applies to a supply and the registered agreements.
 */
@Schema(requiredProperties = {"id", "supply", "type", "start", "end", "observedBeta", "observedValidDays",
        "expectedBeta"})
public class SharingAnomalyResponse {

    @Schema(description = "Identifier derived from the supply, the type and the first anomalous day: the same " +
            "anomaly keeps it from one read to the next", example = "5f0c2d4e-8a1b-3c9d-9e7f-1a2b3c4d5e6f")
    private final UUID id;

    @Schema(description = "Supply the anomaly was observed on")
    private final SupplyReferenceResponse supply;

    @Schema(description = "Kind of anomaly", example = "UNKNOWN_COEFFICIENT")
    private final SharingAnomalyType type;

    @Schema(description = "When it started")
    private final ObservedDateResponse start;

    @Schema(description = "Its last local day. Null when it lasts until the end of the analysed data.",
            example = "2024-05-14", types = {"string", "null"})
    private final LocalDate end;

    @Schema(description = "Median of the supply's daily observed β (E / G) over the anomaly's valid days, from the " +
            "latest day of its start to its last day, or to the supply's last valid day when it lasts until the end " +
            "of the analysed data. On a 0-1 scale, rounded half-up to 6 decimals; for an even number of days, the " +
            "mean of the two middle values.", example = "0.041200")
    private final BigDecimal observedBeta;

    @Schema(description = "Number of valid days the observed β rests on", example = "5")
    private final int observedValidDays;

    @Schema(description = "The supply's registered coefficient in the agreement the anomaly is attributed to, or 0 " +
            "when the supply holds no row in it; for APPLIED_BEFORE_SENT, its value in the agreement sent late. " +
            "The value the supply should have received, to read next to observedBeta. On a 0-1 scale, rounded " +
            "half-up to 6 decimals. Null when the anomaly is attributed to no agreement.",
            example = "0.031500",
            types = {"number", "null"})
    private final BigDecimal expectedBeta;

    public SharingAnomalyResponse(SharingAnomaly anomaly) {
        this.id = anomaly.id();
        this.supply = new SupplyReferenceResponse(anomaly.supply().id(), anomaly.supply().code(),
                anomaly.supply().name());
        this.type = anomaly.type();
        this.start = new ObservedDateResponse(anomaly.start());
        this.end = anomaly.end();
        this.observedBeta = anomaly.observedBeta().value();
        this.observedValidDays = anomaly.observedBeta().validDays();
        this.expectedBeta = anomaly.expectedBeta();
    }

    public UUID getId() {
        return id;
    }

    public SupplyReferenceResponse getSupply() {
        return supply;
    }

    public SharingAnomalyType getType() {
        return type;
    }

    public ObservedDateResponse getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public BigDecimal getObservedBeta() {
        return observedBeta;
    }

    public int getObservedValidDays() {
        return observedValidDays;
    }

    public BigDecimal getExpectedBeta() {
        return expectedBeta;
    }
}
