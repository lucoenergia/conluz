package org.lucoenergia.conluz.infrastructure.admin.community.membership.payback;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.payback.GetMembershipPaybackService;
import org.lucoenergia.conluz.domain.admin.community.membership.payback.MembershipPayback;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.consumption.SupplySavings;
import org.lucoenergia.conluz.domain.consumption.savings.SupplySavingsCalculator;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.time.ClockProvider;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Prices every supply the member owns in the community over the whole period the community has
 * been sharing energy, and measures the total against the investment recorded on their membership.
 *
 * <h2>Accepted limitation: the rate is diluted for members who joined late</h2>
 *
 * <p>The start date is a property of the <em>community</em> -- its first activated coefficient --
 * not of the membership, because a membership has no date of its own to use instead. A member who
 * joined two years after the community began sharing therefore has their savings divided by two
 * years of elapsed days rather than by the time they have actually been participating. Their
 * apparent daily rate is lower than their real one, and the remaining-months estimate that follows
 * from it is correspondingly pessimistic.
 *
 * <p>This is accepted for now rather than worked around. The alternatives all require a fact the
 * data does not currently record: when this member started participating. Guessing it from their
 * first coefficient, their first stored consumption record or their first supply would each
 * silently answer a slightly different question, and none of them survives a member whose supply
 * changed hands. Once a membership carries a start date of its own, that date replaces this one
 * here and nothing else changes.
 *
 * <h2>Query cost</h2>
 *
 * <p>One coefficient query, one supply query, and then one pricing pass per supply -- each of
 * which issues one aggregate query per tariff segment covering the period. With the single-segment
 * resolver shipping today that is one query per supply, so a member with three supplies costs five
 * queries. Accepted for the MVP: the figure is read by one member at a time, not in a list.
 */
@Service
@Transactional(readOnly = true)
public class GetMembershipPaybackServiceImpl implements GetMembershipPaybackService {

    private final GetMembershipsRepository getMembershipsRepository;
    private final GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final SupplySavingsCalculator supplySavingsCalculator;
    private final ZoneResolver zoneResolver;
    private final ClockProvider clockProvider;

    public GetMembershipPaybackServiceImpl(
            GetMembershipsRepository getMembershipsRepository,
            GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository,
            GetSupplyRepository getSupplyRepository,
            SupplySavingsCalculator supplySavingsCalculator,
            ZoneResolver zoneResolver,
            ClockProvider clockProvider) {
        this.getMembershipsRepository = getMembershipsRepository;
        this.getSupplyPartitionCoefficientRepository = getSupplyPartitionCoefficientRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.supplySavingsCalculator = supplySavingsCalculator;
        this.zoneResolver = zoneResolver;
        this.clockProvider = clockProvider;
    }

    @Override
    public MembershipPayback getPayback(UUID communityId, UUID userId) {

        CommunityMembership membership = getMembershipsRepository
                .findByUserIdAndCommunityId(userId, communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId));

        // Read once and shared by the start date and by "today", so the two cannot disagree about
        // which day it is, and so a request spanning midnight is priced against a single instant.
        Instant now = clockProvider.now();
        ZoneId zone = zoneResolver.resolveZoneIdForCommunity(communityId);

        Optional<Instant> startInstant = getSupplyPartitionCoefficientRepository
                .findEarliestValidFromByCommunityId(communityId);

        if (startInstant.isEmpty()) {
            // The community has never shared energy, so there is no period to price. The amount is
            // absent rather than zero: zero would claim the member saved nothing over a period
            // that does not exist.
            return MembershipPayback.of(membership.getInvestmentEur(), null, null,
                    now.atZone(zone).toLocalDate(), TariffSource.ESTIMATE);
        }

        List<Supply> supplies = getSupplyRepository
                .findAllByOwnerAndCommunityId(UserId.of(userId), communityId);

        SupplySavings total = sumSavings(supplies, startInstant.get(), now);

        return MembershipPayback.of(
                membership.getInvestmentEur(),
                total.getAmountEur(),
                startInstant.get().atZone(zone).toLocalDate(),
                now.atZone(zone).toLocalDate(),
                total.getTariffSource());
    }

    /**
     * The member's savings over {@code [from, now)}, summed across their supplies without rounding
     * in between.
     *
     * <p>A member with no supplies has saved zero, not an unknown amount: the period exists and
     * they took nothing from it. That is a different statement from the absent amount returned
     * when the community has no period at all.
     *
     * <p>The source aggregates the same way it does within one supply: any estimated part makes
     * the whole total an estimate, since a total is only as trustworthy as its least trustworthy
     * part. With no supplies to ask, it falls back to {@code ESTIMATE}, matching
     * {@link SupplySavings#unpriced()} -- claiming a real tariff for a figure no tariff was
     * consulted for is the one direction that actively misleads.
     */
    private SupplySavings sumSavings(List<Supply> supplies, Instant from, Instant now) {
        if (!from.isBefore(now)) {
            // The community's first activation is in the future, or exactly now: a period with no
            // instants in it, which the calculator cannot be asked to price.
            return SupplySavings.of(BigDecimal.ZERO, TariffSource.ESTIMATE);
        }

        BigDecimal amount = BigDecimal.ZERO;
        boolean anyEstimated = supplies.isEmpty();
        for (Supply supply : supplies) {
            SupplySavings savings = supplySavingsCalculator.estimate(supply, from, now);
            amount = amount.add(savings.getAmountEur());
            anyEstimated = anyEstimated || savings.getTariffSource() == TariffSource.ESTIMATE;
        }

        return SupplySavings.of(amount, anyEstimated ? TariffSource.ESTIMATE : TariffSource.REAL_TARIFF);
    }
}
