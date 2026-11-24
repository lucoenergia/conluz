package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesEffects;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.CoefficientDatesOutcome;
import org.lucoenergia.conluz.domain.production.sharingagreement.dates.LeavingClosureSource;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.PartitionCoefficientReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SharingAgreementReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.SupplyReferenceResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every effect of setting an agreement's coefficient dates: as projected by the preview, as written by the write.
 */
@Schema(requiredProperties = {"coefficients", "neighbours", "leavingClosures", "agreementStatuses", "communityStart"})
public class CoefficientDatesEffectsResponse {

    @Schema(description = "The requested coefficients whose period changes. A coefficient whose validFrom is " +
            "already the requested one is not listed.")
    private final List<CoefficientPeriodChangeResponse> coefficients;

    @Schema(description = "Every other coefficient, in any agreement, whose end the placements move: the previous " +
            "coefficient of the same supply, shortened to the new start, or the former neighbour of a corrected " +
            "coefficient, joined to its end.")
    private final List<NeighbourChangeResponse> neighbours;

    @Schema(description = "The coefficients of the supplies absent from the agreement whose end the request sets: " +
            "listed in the request (REQUEST), or omitted on the agreement's first activation (DEFAULT).")
    private final List<LeavingClosureChangeResponse> leavingClosures;

    @Schema(description = "Every agreement owning a changed coefficient, with its status before and after; both are " +
            "equal when the status does not change.")
    private final List<AgreementStatusChangeResponse> agreementStatuses;

    @Schema(description = "The earliest validFrom of the plant's community, before and after.")
    private final CommunityStartChangeResponse communityStart;

    public CoefficientDatesEffectsResponse(CoefficientDatesOutcome outcome) {
        CoefficientDatesEffects effects = outcome.effects();
        Map<UUID, SharingAgreementStatus> statusesAfter = new HashMap<>();
        effects.agreements().forEach(change -> statusesAfter.put(change.sharingAgreementId(), change.statusAfter()));
        References references = new References(outcome.supplies(), outcome.agreements(), statusesAfter);
        this.coefficients = effects.coefficients().stream()
                .map(change -> new CoefficientPeriodChangeResponse(change, references)).toList();
        this.neighbours = effects.neighbours().stream()
                .map(change -> new NeighbourChangeResponse(change, references)).toList();
        this.leavingClosures = effects.leavingClosures().stream()
                .map(change -> new LeavingClosureChangeResponse(change, references)).toList();
        this.agreementStatuses = effects.agreements().stream()
                .map(change -> new AgreementStatusChangeResponse(change, references)).toList();
        this.communityStart = new CommunityStartChangeResponse(effects.communityStart());
    }

    public List<CoefficientPeriodChangeResponse> getCoefficients() {
        return coefficients;
    }

    public List<NeighbourChangeResponse> getNeighbours() {
        return neighbours;
    }

    public List<LeavingClosureChangeResponse> getLeavingClosures() {
        return leavingClosures;
    }

    public List<AgreementStatusChangeResponse> getAgreementStatuses() {
        return agreementStatuses;
    }

    public CommunityStartChangeResponse getCommunityStart() {
        return communityStart;
    }

    /**
     * An agreement reference carries the status the agreement has after the request.
     */
    private record References(Map<UUID, Supply> supplies, Map<UUID, SharingAgreement> agreements,
                              Map<UUID, SharingAgreementStatus> statusesAfter) {

        SupplyReferenceResponse supply(UUID id) {
            Supply supply = supplies.get(id);
            return new SupplyReferenceResponse(id, supply.getCode(), supply.getName());
        }

        SharingAgreementReferenceResponse agreement(UUID id) {
            return new SharingAgreementReferenceResponse(id, agreements.get(id).getName(), statusesAfter.get(id));
        }
    }

    @Schema(requiredProperties = {"coefficient", "supply", "validFromBefore", "validToBefore", "validFromAfter",
            "validToAfter", "leavesDistribution"})
    public static class CoefficientPeriodChangeResponse {

        @Schema(description = "The requested coefficient")
        private final PartitionCoefficientReferenceResponse coefficient;

        @Schema(description = "The coefficient's supply")
        private final SupplyReferenceResponse supply;

        @Schema(description = "validFrom before the request. Null when the coefficient was pending.",
                example = "2025-02-28T23:00:00Z", types = {"string", "null"})
        private final Instant validFromBefore;

        @Schema(description = "validTo before the request. Null when open or pending.",
                example = "2025-12-31T23:00:00Z", types = {"string", "null"})
        private final Instant validToBefore;

        @Schema(description = "validFrom after the request", example = "2025-02-28T23:00:00Z")
        private final Instant validFromAfter;

        @Schema(description = "validTo after the request. Null when open.", example = "2025-12-31T23:00:00Z",
                types = {"string", "null"})
        private final Instant validToAfter;

        @Schema(description = "Whether the coefficient takes its supply out of the distribution: its value is 0 and, " +
                "in the PUBLISHED or SUPERSEDED agreement immediately before its own in registered order, the supply " +
                "holds a coefficient greater than 0. Reported for the coefficients this request places; a supply " +
                "absent from the previous agreement had already left it there.", example = "false")
        private final boolean leavesDistribution;

        CoefficientPeriodChangeResponse(CoefficientDatesEffects.CoefficientPeriodChange change, References references) {
            this.coefficient = new PartitionCoefficientReferenceResponse(change.coefficientId());
            this.supply = references.supply(change.supplyId());
            this.validFromBefore = change.validFromBefore();
            this.validToBefore = change.validToBefore();
            this.validFromAfter = change.validFromAfter();
            this.validToAfter = change.validToAfter();
            this.leavesDistribution = change.leavesDistribution();
        }

        public PartitionCoefficientReferenceResponse getCoefficient() {
            return coefficient;
        }

        public SupplyReferenceResponse getSupply() {
            return supply;
        }

        public Instant getValidFromBefore() {
            return validFromBefore;
        }

        public Instant getValidToBefore() {
            return validToBefore;
        }

        public Instant getValidFromAfter() {
            return validFromAfter;
        }

        public Instant getValidToAfter() {
            return validToAfter;
        }

        public boolean isLeavesDistribution() {
            return leavesDistribution;
        }
    }

    @Schema(requiredProperties = {"coefficient", "supply", "sharingAgreement", "validToBefore", "validToAfter"})
    public static class NeighbourChangeResponse {

        @Schema(description = "The coefficient whose end moves")
        private final PartitionCoefficientReferenceResponse coefficient;

        @Schema(description = "The coefficient's supply")
        private final SupplyReferenceResponse supply;

        @Schema(description = "The agreement owning the coefficient, with its status after the request")
        private final SharingAgreementReferenceResponse sharingAgreement;

        @Schema(description = "validTo before the request. Null when open.", example = "2025-12-31T23:00:00Z",
                types = {"string", "null"})
        private final Instant validToBefore;

        @Schema(description = "validTo after the request. Null when open.", example = "2025-02-28T23:00:00Z",
                types = {"string", "null"})
        private final Instant validToAfter;

        NeighbourChangeResponse(CoefficientDatesEffects.NeighbourChange change, References references) {
            this.coefficient = new PartitionCoefficientReferenceResponse(change.coefficientId());
            this.supply = references.supply(change.supplyId());
            this.sharingAgreement = references.agreement(change.sharingAgreementId());
            this.validToBefore = change.validToBefore();
            this.validToAfter = change.validToAfter();
        }

        public PartitionCoefficientReferenceResponse getCoefficient() {
            return coefficient;
        }

        public SupplyReferenceResponse getSupply() {
            return supply;
        }

        public SharingAgreementReferenceResponse getSharingAgreement() {
            return sharingAgreement;
        }

        public Instant getValidToBefore() {
            return validToBefore;
        }

        public Instant getValidToAfter() {
            return validToAfter;
        }
    }

    @Schema(requiredProperties = {"supply", "coefficient", "sharingAgreement", "validToBefore", "validToAfter",
            "source"})
    public static class LeavingClosureChangeResponse {

        @Schema(description = "The supply absent from the agreement")
        private final SupplyReferenceResponse supply;

        @Schema(description = "The supply's coefficient before the agreement, whose end is set")
        private final PartitionCoefficientReferenceResponse coefficient;

        @Schema(description = "The agreement owning that coefficient, with its status after the request")
        private final SharingAgreementReferenceResponse sharingAgreement;

        @Schema(description = "validTo before the request. Null when open.", example = "2025-12-31T23:00:00Z",
                types = {"string", "null"})
        private final Instant validToBefore;

        @Schema(description = "validTo after the request", example = "2025-02-28T23:00:00Z")
        private final Instant validToAfter;

        @Schema(description = "REQUEST when the request listed the supply; DEFAULT when the request is the " +
                "agreement's first activation and omitted it, so it is closed at the request's earliest date.",
                example = "REQUEST")
        private final LeavingClosureSource source;

        LeavingClosureChangeResponse(CoefficientDatesEffects.LeavingClosureChange change, References references) {
            this.supply = references.supply(change.supplyId());
            this.coefficient = new PartitionCoefficientReferenceResponse(change.coefficientId());
            this.sharingAgreement = references.agreement(change.sharingAgreementId());
            this.validToBefore = change.validToBefore();
            this.validToAfter = change.validToAfter();
            this.source = change.source();
        }

        public SupplyReferenceResponse getSupply() {
            return supply;
        }

        public PartitionCoefficientReferenceResponse getCoefficient() {
            return coefficient;
        }

        public SharingAgreementReferenceResponse getSharingAgreement() {
            return sharingAgreement;
        }

        public Instant getValidToBefore() {
            return validToBefore;
        }

        public Instant getValidToAfter() {
            return validToAfter;
        }

        public LeavingClosureSource getSource() {
            return source;
        }
    }

    @Schema(requiredProperties = {"sharingAgreement", "statusBefore", "statusAfter"})
    public static class AgreementStatusChangeResponse {

        @Schema(description = "The agreement, with its status after the request")
        private final SharingAgreementReferenceResponse sharingAgreement;

        @Schema(description = "Status before the request", example = "PUBLISHED")
        private final SharingAgreementStatus statusBefore;

        @Schema(description = "Status after the request", example = "SUPERSEDED")
        private final SharingAgreementStatus statusAfter;

        AgreementStatusChangeResponse(CoefficientDatesEffects.AgreementStatusChange change, References references) {
            this.sharingAgreement = references.agreement(change.sharingAgreementId());
            this.statusBefore = change.statusBefore();
            this.statusAfter = change.statusAfter();
        }

        public SharingAgreementReferenceResponse getSharingAgreement() {
            return sharingAgreement;
        }

        public SharingAgreementStatus getStatusBefore() {
            return statusBefore;
        }

        public SharingAgreementStatus getStatusAfter() {
            return statusAfter;
        }
    }

    @Schema(requiredProperties = {"before", "after"})
    public static class CommunityStartChangeResponse {

        @Schema(description = "The earliest validFrom of the community's coefficients before the request. Null " +
                "when none has started.", example = "2024-12-31T23:00:00Z", types = {"string", "null"})
        private final Instant before;

        @Schema(description = "The earliest validFrom of the community's coefficients after the request. Null " +
                "when none has started.", example = "2024-12-31T23:00:00Z", types = {"string", "null"})
        private final Instant after;

        CommunityStartChangeResponse(CoefficientDatesEffects.CommunityStartChange change) {
            this.before = change.before();
            this.after = change.after();
        }

        public Instant getBefore() {
            return before;
        }

        public Instant getAfter() {
            return after;
        }
    }
}
