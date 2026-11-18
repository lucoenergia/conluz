package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.AnalysisStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.NotComputableReason;
import org.lucoenergia.conluz.domain.production.sharingagreement.observation.ObservedSupply;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * What the observation of the plant could analyse.
 */
@Schema(requiredProperties = {"computable", "notComputableReason", "analysedFrom", "analysedTo", "suppliesWithValidDays",
        "suppliesWithoutValidDays", "notComputableSupplies"})
public class SharingObservationAnalysisResponse {

    @Schema(description = "False when nothing could be observed, because the plant supply has no data")
    private final boolean computable;

    @Schema(description = "Why nothing could be observed. Null when computable.",
            example = "PLANT_SUPPLY_WITHOUT_DATA", types = {"string", "null"})
    private final NotComputableReason notComputableReason;

    @Schema(description = "First local day analysed: the day of the plant supply's first production record. Null " +
            "when not computable.", example = "2024-02-01", types = {"string", "null"})
    private final LocalDate analysedFrom;

    @Schema(description = "Last local day analysed: the end of the latest month published for the plant supply. " +
            "Null when not computable.", example = "2026-10-31", types = {"string", "null"})
    private final LocalDate analysedTo;

    @Schema(description = "Analysed supplies with at least one valid day")
    private final List<SupplyReferenceResponse> suppliesWithValidDays;

    @Schema(description = "Analysed supplies without any valid day")
    private final List<SupplyReferenceResponse> suppliesWithoutValidDays;

    @Schema(description = "Supplies holding coefficients in this plant and in another one: the energy they receive " +
            "cannot be attributed to this plant alone, so they are not analysed. A supply holding coefficients only " +
            "in another plant is not part of this plant's analysis and is not listed anywhere.")
    private final List<SupplyReferenceResponse> notComputableSupplies;

    public SharingObservationAnalysisResponse(AnalysisStatus analysis) {
        this.computable = analysis.computable();
        this.notComputableReason = analysis.notComputableReason();
        this.analysedFrom = analysis.analysedFrom();
        this.analysedTo = analysis.analysedTo();
        this.suppliesWithValidDays = references(analysis.suppliesWithData());
        this.suppliesWithoutValidDays = references(analysis.suppliesWithoutData());
        this.notComputableSupplies = references(analysis.notComputableSupplies());
    }

    private static List<SupplyReferenceResponse> references(List<ObservedSupply> supplies) {
        return supplies.stream()
                .map(supply -> new SupplyReferenceResponse(supply.id(), supply.code(), supply.name()))
                .toList();
    }

    public boolean isComputable() {
        return computable;
    }

    public NotComputableReason getNotComputableReason() {
        return notComputableReason;
    }

    public LocalDate getAnalysedFrom() {
        return analysedFrom;
    }

    public LocalDate getAnalysedTo() {
        return analysedTo;
    }

    public List<SupplyReferenceResponse> getSuppliesWithValidDays() {
        return suppliesWithValidDays;
    }

    public List<SupplyReferenceResponse> getSuppliesWithoutValidDays() {
        return suppliesWithoutValidDays;
    }

    public List<SupplyReferenceResponse> getNotComputableSupplies() {
        return notComputableSupplies;
    }
}
