package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;

import java.util.UUID;

/**
 * Access rules for a plant. The plant is passed already resolved, and {@code null} stands for "the
 * lookup found nothing", treated exactly like a plant the caller may not see.
 *
 * <p>Plant visibility runs through membership, not {@code canSeeCommunity}: a platform admin who is
 * not a member of the plant's community cannot see the plant at all. A plant whose supply carries no
 * community is visible to nobody, since no membership can match a null community.</p>
 */
public class PlantAccessPolicy {

    private final SupplyAccessPolicy supplyAccessPolicy;

    public PlantAccessPolicy(SupplyAccessPolicy supplyAccessPolicy) {
        this.supplyAccessPolicy = supplyAccessPolicy;
    }

    /**
     * Reading a plant is open to any enabled member of its community, whatever their role.
     */
    public AccessDecision canRead(User caller, Plant plant) {
        return isVisible(caller, plant) ? AccessDecision.ALLOWED : AccessDecision.NOT_VISIBLE;
    }

    /**
     * Managing a plant belongs to the enabled community admins of its community. A plain member can
     * see the plant, so they are forbidden rather than not-found.
     */
    public AccessDecision canManage(User caller, Plant plant) {
        if (!isVisible(caller, plant)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityOf(plant))
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Creating a plant under a supply. The <em>supply</em> is the resource whose existence must not
     * leak, so a caller who can see neither its community nor owns it gets not-found; an owner who
     * is not a community admin can see it but may not create plants on it, so they are forbidden.
     */
    public AccessDecision canCreate(User caller, Supply supply) {
        if (!supplyAccessPolicy.isVisible(caller, supply)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return supplyAccessPolicy.isCommunityAdminOf(caller, supply)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Listing the plants of a community is open to any enabled member. A platform admin who is not a
     * member can see the community, so they are forbidden rather than not-found — unlike a single
     * plant, where the same caller gets not-found.
     */
    public AccessDecision canList(User caller, UUID communityId) {
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasMembershipInCommunity(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * The community a plant belongs to, reached through its supply, or {@code null} when either link
     * is missing.
     */
    public UUID communityOf(Plant plant) {
        return plant != null && plant.getSupply() != null && plant.getSupply().getCommunity() != null
                ? plant.getSupply().getCommunity().getId()
                : null;
    }

    /**
     * The single spelling of "can this caller see this plant at all": it exists, and they hold an
     * enabled membership in its community.
     */
    public boolean isVisible(User caller, Plant plant) {
        return plant != null && CallerMemberships.hasMembershipInCommunity(caller, communityOf(plant));
    }
}
