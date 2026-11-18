package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAnomaly;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.SharingAnomalyType;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A sustained disagreement between what the distributor applies to a supply and the registered agreements.
 */
@Schema(requiredProperties = {"id", "supply", "type", "start", "end"})
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

    public SharingAnomalyResponse(SharingAnomaly anomaly) {
        this.id = anomaly.id();
        this.supply = new SupplyReferenceResponse(anomaly.supply().id(), anomaly.supply().code(),
                anomaly.supply().name());
        this.type = anomaly.type();
        this.start = new ObservedDateResponse(anomaly.start());
        this.end = anomaly.end();
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
}
