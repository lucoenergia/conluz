package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.time.Instant;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GetSupplyPartitionCoefficientRepository {

    /**
     * The coefficient for a specific {@code (plantId, supplyId)} pair active at {@code timestamp}.
     * Scoping by plant is what makes this unambiguous: a supply may have concurrently-active
     * coefficients across multiple plants, permitted by the {@code no_overlapping_coefficients}
     * exclusion constraint, which is scoped to {@code (plant_id, supply_id)}, not {@code supply_id}
     * alone.
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
     * The earliest activation instant among the partition coefficients of a community's plants --
     * the point from which that community has been sharing energy at all, and therefore the point
     * a member's savings start accruing from.
     *
     * <p>Pending coefficients ({@code validFrom IS NULL}) do not count: a coefficient that has
     * been authored but never applied by the distributor has not saved anyone anything yet.
     *
     * <p>Scoped by the <em>plant's</em> community, which a plant belongs to through its own
     * supply. Empty when the community has no plant carrying an activated coefficient, which is
     * a legitimate state (a community that has not started sharing) rather than an error.
     */
    Optional<Instant> findEarliestValidFromByCommunityId(UUID communityId);

    /**
     * {@link #findEarliestValidFromByCommunityId} over every plant of the community except {@code plantId}: what the
     * community's start becomes once that plant's rows are replaced by a projection of them.
     */
    Optional<Instant> findEarliestValidFromByCommunityIdExcludingPlantId(UUID communityId, UUID plantId);

    /**
     * Read-only existence check used by the sharing-agreement publish precondition. Phase 5c's
     * coefficient-materialization work should extend this repository rather than adding a
     * parallel one.
     */
    boolean existsBySharingAgreementId(UUID sharingAgreementId);

    /**
     * Read-only existence check used by the sharing-agreement revert-to-draft precondition: an
     * agreement can only go back to DRAFT while none of its coefficients have been applied by the
     * distributor yet (i.e. none has {@code validFrom} set).
     */
    boolean existsBySharingAgreementIdAndValidFromIsNotNull(UUID sharingAgreementId);

    /**
     * The subset of {@code ids} that belong to {@code sharingAgreementId}. Ownership is enforced by
     * this query itself (not by a service-side check afterwards) so it cannot be silently dropped by
     * a later refactor: any id absent from the result -- unknown, or belonging to another agreement
     * -- is exactly the caller's "not in this agreement" set.
     * <p>
     * Two coefficients returned here can never share a {@code (plantId, supplyId)} pair: within one
     * sharing agreement, {@code supply_id} is unique (SHA-018), enforced by the database constraint
     * on {@code (sharing_agreement_id, supply_id)}, so it holds for every row however it was
     * written. Callers scoped to one {@code sharingAgreementId} (as every activation batch is) therefore need
     * no additional same-batch collision check for two different ids resolving the same predecessor.
     */
    List<SupplyPartitionCoefficient> findAllByIdAndSharingAgreementId(List<UUID> ids, UUID sharingAgreementId);

    /**
     * The activated coefficient, for {@code (plantId, supplyId)}, whose current {@code validTo} equals
     * {@code boundaryValidTo}, which must not be null. Used by deactivation to splice the chain: the
     * row that ends where the deactivated coefficient starts takes over its former {@code validTo}.
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
     * non-adjacent activation for the same supply) would silently overlap it. {@code setValidFrom}
     * does not use this: it places a coefficient against the whole timeline of its supply
     * ({@link CoefficientPlacement}), loaded with {@link #findAllByPlantId}.
     */
    Optional<SupplyPartitionCoefficient> findNextActivatedAfter(UUID plantId, UUID supplyId, UUID excludeCoefficientId,
                                                                   Instant afterInstant);

    /**
     * Every coefficient of one sharing agreement, in no particular order.
     */
    List<SupplyPartitionCoefficient> findAllBySharingAgreementId(UUID sharingAgreementId);

    /**
     * Every coefficient row -- any agreement, any time, pending or not -- for {@code plantId} and
     * any of {@code supplyIds}. Used to project a batch's writes onto the complete history for the
     * supplies it touches, so {@link CoefficientOverlapDetector} can check for an overlap against
     * the whole chain, not just the immediate predecessor/successor.
     */
    List<SupplyPartitionCoefficient> findAllByPlantIdAndSupplyIdIn(UUID plantId, Collection<UUID> supplyIds);

    /**
     * Every coefficient row of {@code plantId} -- any agreement, any status, any time, pending or not.
     */
    List<SupplyPartitionCoefficient> findAllByPlantId(UUID plantId);

    /**
     * Those of {@code supplyIds} that hold a coefficient row in a plant other than {@code plantId}, in
     * any agreement, at any time. An empty collection yields an empty set.
     */
    Set<UUID> findSupplyIdsWithCoefficientsInOtherPlants(UUID plantId, Collection<UUID> supplyIds);

    /**
     * The coefficient history of a supply, enriched with supply, plant and agreement display data,
     * ordered by valid_from ascending. A null {@code plantId} means every plant the supply
     * participates in; a plant the supply has no coefficient in yields an empty list.
     *
     * <p>{@code includePending} selects whether pending rows (valid_from IS NULL) are part of the
     * timeline. They are authored inside a DRAFT agreement and never applied by the distributor, so
     * only a Community Admin of the supply's community may see them; the caller decides, and the
     * filter is applied by the query rather than after the fact.
     */
    List<SupplyPartitionCoefficientDetail> findAllDetailsBySupplyId(UUID supplyId, UUID plantId,
                                                                    boolean includePending);

    /**
     * The active coefficient of a supply in each plant it participates in -- at most one per plant,
     * guaranteed by the {@code no_overlapping_coefficients} exclusion constraint. A null
     * {@code plantId} means every plant.
     *
     * <p>Active means {@code validFrom IS NOT NULL AND validTo IS NULL}. A pending row also has a
     * null valid_to, so the valid_from predicate is what separates "in force" from "authored but
     * never applied"; without it a pending row would be reported as the supply's active coefficient.
     */
    List<SupplyPartitionCoefficientDetail> findActiveDetailsBySupplyId(UUID supplyId, UUID plantId);

    /**
     * One item per plant whose coefficient for {@code supplyId} covers {@code timestamp}, with
     * valid_from inclusive and valid_to exclusive. A null {@code plantId} means every plant. Pending
     * rows are excluded: a coefficient never applied by the distributor covered no instant.
     */
    List<SupplyPartitionCoefficientDetail> findDetailsBySupplyIdAtTimestamp(UUID supplyId, UUID plantId, Instant timestamp);

    /**
     * Details for the given coefficient ids, in no particular order. Used to enrich the result of a
     * write in one query rather than per row; callers that care about order must restore it
     * themselves, since SQL {@code IN} does not preserve argument order.
     */
    List<SupplyPartitionCoefficientDetail> findAllDetailsByIdIn(Collection<UUID> ids);

    /**
     * The active coefficient of each of {@code supplyIds} within one plant, in a single query -- the
     * batch behind a sharing agreement's "current coefficient" column. At most one row per supply,
     * for the reason given on {@link #findActiveDetailsBySupplyId}.
     */
    List<SupplyPartitionCoefficientDetail> findActiveDetailsByPlantIdAndSupplyIdIn(UUID plantId, Collection<UUID> supplyIds);

    /**
     * Every coefficient in force in {@code plantId}, one per supply, ordered by CUPS ascending -- the
     * unfiltered sibling of {@link #findActiveDetailsByPlantIdAndSupplyIdIn}. At most one row per
     * supply, for the reason given on {@link #findActiveDetailsBySupplyId}.
     *
     * <p>Active means {@code validFrom IS NOT NULL AND validTo IS NULL}, exactly as elsewhere in this
     * port: pending rows (authored but never applied by the distributor) also carry a null valid_to,
     * so the valid_from predicate is what separates them from the rows actually in force.
     *
     * <p>The authoring agreement's status is not a filter. A plant's newest agreement is often only
     * partially applied, so supplies legitimately sit on coefficients authored by different
     * agreements, and those coefficients do not have to sum to 1.
     *
     * <p>Empty when the plant has nothing in force -- a plant that has not started sharing yet, not
     * an error.
     */
    List<SupplyPartitionCoefficientDetail> findActiveDetailsByPlantId(UUID plantId);

    /**
     * Whether any sharing agreement, in any status, holds a partition coefficient for the given supply.
     */
    boolean existsBySupply(SupplyId supplyId);

    /**
     * Whether any partition coefficient, in any sharing agreement, references the given plant.
     */
    boolean existsByPlant(PlantId plantId);
}
