package org.lucoenergia.conluz.domain.production.sharingagreement.dates;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Every effect of setting an agreement's coefficient dates: the same object is returned by the preview, as projected,
 * and by the write, as written. Only rows whose period changes are listed.
 *
 * @param coefficients    the requested coefficients whose period changes
 * @param neighbours      every other row whose end the placements moved, in any agreement
 * @param leavingClosures the rows of the supplies absent from the agreement closed by the request
 * @param agreements      every agreement owning a changed row, with its status before and after
 * @param communityStart  the earliest validFrom of the plant's community, before and after
 */
public record CoefficientDatesEffects(List<CoefficientPeriodChange> coefficients,
                                      List<NeighbourChange> neighbours,
                                      List<LeavingClosureChange> leavingClosures,
                                      List<AgreementStatusChange> agreements,
                                      CommunityStartChange communityStart) {

    public CoefficientDatesEffects {
        coefficients = List.copyOf(coefficients);
        neighbours = List.copyOf(neighbours);
        leavingClosures = List.copyOf(leavingClosures);
        agreements = List.copyOf(agreements);
        Objects.requireNonNull(communityStart, "communityStart");
    }

    /**
     * A requested coefficient's period, before and after. A pending coefficient has no validFrom before.
     *
     * @param leavesDistribution whether the coefficient drops its supply to 0 after a non-zero value in the previous
     *                           agreement in registered order (SHA-020)
     */
    public record CoefficientPeriodChange(UUID coefficientId, UUID supplyId,
                                          Instant validFromBefore, Instant validToBefore,
                                          Instant validFromAfter, Instant validToAfter,
                                          boolean leavesDistribution) {
    }

    /**
     * A row of the same supply as a requested coefficient whose end the placement moved (SHA-003, SHA-004).
     */
    public record NeighbourChange(UUID coefficientId, UUID supplyId, UUID sharingAgreementId,
                                  Instant validToBefore, Instant validToAfter) {
    }

    /**
     * The row of a supply absent from the agreement whose end the request set.
     */
    public record LeavingClosureChange(UUID supplyId, UUID coefficientId, UUID sharingAgreementId,
                                       Instant validToBefore, Instant validToAfter, LeavingClosureSource source) {
    }

    /**
     * The status of an agreement owning a changed row; both are equal when the status does not change.
     */
    public record AgreementStatusChange(UUID sharingAgreementId, SharingAgreementStatus statusBefore,
                                        SharingAgreementStatus statusAfter) {
    }

    /**
     * The earliest validFrom of the community's coefficients, before and after; null when there is none.
     */
    public record CommunityStartChange(Instant before, Instant after) {
    }
}
