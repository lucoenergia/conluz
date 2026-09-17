package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

public interface SupplyAccessGuard {

    boolean canReadSupply(UUID supplyId);

    boolean canEditSupply(UUID supplyId);

    /**
     * Whether the current user is a Community Admin of the supply's community. Unlike
     * {@link #canReadSupply(UUID)} and {@link #canEditSupply(UUID)} this never throws and never
     * decides whether a request proceeds: it answers a question a controller asks <em>after</em> its
     * gate has passed, to shape a response the caller is already entitled to. Anonymous callers and
     * unknown supplies are simply {@code false}.
     */
    boolean isCommunityAdminOfSupply(UUID supplyId);
}
