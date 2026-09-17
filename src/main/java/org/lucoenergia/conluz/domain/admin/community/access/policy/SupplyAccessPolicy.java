package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

/**
 * Access rules for a supply. The supply is passed already resolved, and {@code null} stands for
 * "the lookup found nothing" — which the rules treat exactly like a supply the caller may not see,
 * so that a missing supply and an invisible one are indistinguishable from outside.
 *
 * <p>There is no platform-admin bypass here: a platform admin who neither administers the supply's
 * community nor owns it cannot see it.</p>
 */
public class SupplyAccessPolicy {

    /**
     * Reading a supply is open to its owner and to the enabled community admins of its community —
     * the same access editing requires, so a caller who may not read it is never told it exists.
     */
    public AccessDecision canRead(User caller, Supply supply) {
        return isVisible(caller, supply) ? AccessDecision.ALLOWED : AccessDecision.NOT_VISIBLE;
    }

    /**
     * Editing is narrower than reading: an owner can see their supply but only a community admin may
     * change it, so an owner is forbidden rather than not-found.
     */
    public AccessDecision canEdit(User caller, Supply supply) {
        if (!isVisible(caller, supply)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return isCommunityAdminOf(caller, supply) ? AccessDecision.ALLOWED : AccessDecision.FORBIDDEN;
    }

    /**
     * Whether the caller administers the supply's community. Never {@link AccessDecision#NOT_VISIBLE}:
     * this answers a question asked <em>after</em> a gate has passed, to shape a response the caller
     * is already entitled to, so an unknown supply is merely forbidden.
     */
    public AccessDecision isCommunityAdminOfSupply(User caller, Supply supply) {
        return supply != null && isCommunityAdminOf(caller, supply)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Reading the partition coefficients of a single supply. Byte-for-byte {@link #canEdit} today,
     * and deliberately kept as its own rule: it is a read, and separating it now means the read and
     * the write can diverge later without touching any call site. Do not merge it away as
     * duplication.
     */
    public AccessDecision canReadPartitionCoefficients(User caller, Supply supply) {
        return canEdit(caller, supply);
    }

    /**
     * The single spelling of "can this caller see this supply at all": it exists, and they either
     * administer its community or own it.
     */
    public boolean isVisible(User caller, Supply supply) {
        return supply != null && (isCommunityAdminOf(caller, supply) || isOwner(caller, supply));
    }

    /**
     * The single spelling of "Community Admin of this supply's community".
     */
    public boolean isCommunityAdminOf(User caller, Supply supply) {
        UUID communityId = supply.getCommunity() != null ? supply.getCommunity().getId() : null;
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId);
    }

    /**
     * The single spelling of "owns this supply", shared with {@link PlantAccessPolicy} — creating a
     * plant asks the same question of the same supply.
     */
    public boolean isOwner(User caller, Supply supply) {
        return supply.getUser() != null && supply.getUser().getId() != null
                && supply.getUser().getId().equals(caller.getId());
    }
}
