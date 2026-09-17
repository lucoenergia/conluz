package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface SupplyAccessGuard {

    boolean canReadSupply(UUID supplyId);

    boolean canEditSupply(UUID supplyId);

    /**
     * Whether the current user may read the partition coefficients of the given supply. This is
     * currently byte-for-byte identical to {@link #canEditSupply(UUID)} -- kept as a separate,
     * deliberately duplicating method (not merged away) so a read rule and a write rule can diverge
     * later without touching call sites; do not delete this method as dead weight. Mirrors the way
     * {@code canReadSharingAgreement} is kept apart from {@code canManageSharingAgreement}.
     */
    boolean canReadSupplyPartitionCoefficients(UUID supplyId);

    /**
     * Whether the current user is a Community Admin of the supply's community. Unlike
     * {@link #canReadSupply(UUID)} and {@link #canEditSupply(UUID)} this never throws and never
     * decides whether a request proceeds: it answers a question a controller asks <em>after</em> its
     * gate has passed, to shape a response the caller is already entitled to. Anonymous callers and
     * unknown supplies are simply {@code false}.
     */
    boolean isCommunityAdminOfSupply(UUID supplyId);
}
