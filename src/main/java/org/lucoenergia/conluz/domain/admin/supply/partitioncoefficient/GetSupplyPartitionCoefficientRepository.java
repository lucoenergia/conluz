package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetSupplyPartitionCoefficientRepository {

    Optional<SupplyPartitionCoefficient> findActiveBySupplyId(UUID supplyId);

    Optional<SupplyPartitionCoefficient> findBySupplyIdAtTimestamp(UUID supplyId, Instant timestamp);

    /**
     * The coefficient for a specific {@code (plantId, supplyId)} pair active at {@code timestamp}.
     * Unlike {@link #findBySupplyIdAtTimestamp}, this is unambiguous even when the supply has
     * concurrently-active coefficients across multiple plants (permitted by the
     * {@code no_overlapping_coefficients} exclusion constraint, which is scoped to
     * {@code (plant_id, supply_id)}, not {@code supply_id} alone).
     */
    Optional<SupplyPartitionCoefficient> findByPlantIdAndSupplyIdAtTimestamp(UUID plantId, UUID supplyId, Instant timestamp);

    /**
     * Every coefficient for {@code supplyId} -- across all of its plants -- active at {@code timestamp}.
     */
    List<SupplyPartitionCoefficient> findAllBySupplyIdAtTimestamp(UUID supplyId, Instant timestamp);

    List<SupplyPartitionCoefficient> findBySupplyIdInRange(UUID supplyId, Instant from, Instant to);

    List<SupplyPartitionCoefficient> findAllBySupplyIdOrderByValidFromAsc(UUID supplyId);

    List<SupplyPartitionCoefficient> findAllActiveAtTimestamp(Instant timestamp);

    /**
     * Read-only existence check used by the sharing-agreement publish precondition. Phase 5c's
     * coefficient-materialization work should extend this repository rather than adding a
     * parallel one.
     */
    boolean existsBySharingAgreementId(UUID sharingAgreementId);

    /**
     * The subset of {@code ids} that belong to {@code sharingAgreementId}. Ownership is enforced by
     * this query itself (not by a service-side check afterwards) so it cannot be silently dropped by
     * a later refactor: any id absent from the result -- unknown, or belonging to another agreement
     * -- is exactly the caller's "not in this agreement" set.
     * <p>
     * Two coefficients returned here can never share a {@code (plantId, supplyId)} pair: within one
     * sharing agreement, {@code supply_id} is unique by construction --
     * {@code MaterializeSharingAgreementCoefficientsServiceImpl.assertNoDuplicateCups} rejects
     * duplicate CUPS within a single materialisation call, and {@code replaceAllForSharingAgreement}
     * is a full delete-and-reinsert that is the sole creator of coefficient rows, so the invariant
     * holds continuously (activation never creates, deletes, or moves a row to another agreement).
     * Callers scoped to one {@code sharingAgreementId} (as every activation batch is) therefore need
     * no additional same-batch collision check for two different ids resolving the same predecessor.
     */
    List<SupplyPartitionCoefficient> findAllByIdAndSharingAgreementId(List<UUID> ids, UUID sharingAgreementId);

    /**
     * The coefficient, for {@code (plantId, supplyId)}, whose current {@code validTo} equals
     * {@code boundaryValidTo} -- or, when {@code boundaryValidTo} is {@code null}, the currently open
     * row (the pure-activation case). This boundary-match definition (rather than "the open row") is
     * required for correcting a coefficient that is not itself the open row, e.g. one belonging to an
     * already-superseded agreement; it is also what lets activate and correct share one code path.
     * {@code excludeCoefficientId} excludes the row being written itself.
     */
    Optional<SupplyPartitionCoefficient> findPredecessor(UUID plantId, UUID supplyId, UUID excludeCoefficientId,
                                                           Instant boundaryValidTo);

    /**
     * The activated ({@code validFrom IS NOT NULL}) coefficient for {@code (plantId, supplyId)} with
     * the smallest {@code validFrom} that is still strictly after {@code afterInstant} -- the nearest
     * later row in the timeline, however far away, or empty if none exists.
     * {@code excludeCoefficientId} excludes the row being written itself.
     * <p>
     * Used by {@code setValidTo}'s correct/reopen path for two purposes at once: (1) when the current
     * {@code validTo} equals this row's {@code validFrom} exactly, the current {@code validTo} was
     * written by the activation cascade, not authored, and correcting/reopening past it must be
     * rejected; (2) independently of (1), the requested new {@code validTo} (or infinity, for reopen)
     * must not reach at or past this row's {@code validFrom} -- otherwise a correction that skips past
     * an intermediate gap (e.g. an explicitly-closed exit-case row later followed by an unrelated,
     * non-adjacent activation for the same supply) would silently overlap it. A predecessor lookup
     * bounded only by the immediately-adjacent row is not equivalent to this: {@code setValidFrom}'s
     * correction is safe with an exact-adjacency check because both of its bounds (predecessor's own
     * {@code validFrom} and the coefficient's own {@code validTo}) come from the *current*, already-
     * valid state and cannot be skipped past; {@code setValidTo}'s correction/reopen can move to an
     * arbitrary later value (or infinity), which can reach past an intermediate row entirely.
     */
    Optional<SupplyPartitionCoefficient> findNextActivatedAfter(UUID plantId, UUID supplyId, UUID excludeCoefficientId,
                                                                   Instant afterInstant);
}
