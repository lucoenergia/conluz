package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;

import java.util.Objects;
import java.util.UUID;

/**
 * Access rules for a sharing agreement, which is always reached through the plant it belongs to.
 *
 * <p>Whether the caller may see the <em>plant</em> is {@link PlantAccessPolicy}'s decision and must
 * be settled first; {@link #canRead} and {@link #canManage} assume a visible plant and decide only
 * about the agreement. That split exists because the two carry different not-found identities — an
 * invisible plant is a missing plant, an agreement under a different plant is a missing agreement —
 * and one {@link AccessDecision} cannot say which.</p>
 *
 * <p>{@link #canReadThroughPlant} and {@link #canManageThroughPlant} compose the two steps, so the
 * composition is written once. A guard calls them and, on {@code NOT_VISIBLE}, asks
 * {@link PlantAccessPolicy#isVisible} which exception to throw; a capability assembler, which has
 * no exception to choose, just reads the decision.</p>
 */
public class SharingAgreementAccessPolicy {

    private final PlantAccessPolicy plantAccessPolicy;

    public SharingAgreementAccessPolicy(PlantAccessPolicy plantAccessPolicy) {
        this.plantAccessPolicy = plantAccessPolicy;
    }

    /**
     * Patching, deleting or publishing an agreement: the enabled community admins of the plant's
     * community. An agreement that does not exist, or that belongs to a different plant, is
     * not-visible — a caller must not learn it exists elsewhere.
     *
     * <p>Assumes the plant is already known to be visible. Use {@link #canManageThroughPlant} when
     * it is not.</p>
     */
    public AccessDecision canManage(User caller, Plant plant, SharingAgreement agreement) {
        if (agreement == null || !Objects.equals(plantIdOf(plant), agreement.getPlantId())) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityOf(plant))
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Reading an agreement. Its contents — coefficients, distributor files, participating supplies'
     * CUPS — are admin-only, so this is byte-for-byte {@link #canManage} today. Kept as its own rule
     * on purpose so a read rule and a write rule can diverge later without touching any call site;
     * do not delete it as duplication.
     *
     * <p>Assumes the plant is already known to be visible. Use {@link #canReadThroughPlant} when it
     * is not.</p>
     */
    public AccessDecision canRead(User caller, Plant plant, SharingAgreement agreement) {
        return canManage(caller, plant, agreement);
    }

    /**
     * The whole decision an endpoint makes when reading an agreement: the plant first, then the
     * agreement.
     */
    public AccessDecision canReadThroughPlant(User caller, Plant plant, SharingAgreement agreement) {
        if (!plantAccessPolicy.isVisible(caller, plant)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return canRead(caller, plant, agreement);
    }

    /**
     * The whole decision an endpoint makes when writing to an agreement: the plant first, then the
     * agreement.
     */
    public AccessDecision canManageThroughPlant(User caller, Plant plant, SharingAgreement agreement) {
        if (!plantAccessPolicy.isVisible(caller, plant)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return canManage(caller, plant, agreement);
    }

    /**
     * Whether a {@code NOT_VISIBLE} from the two {@code …ThroughPlant} methods was the plant's doing
     * rather than the agreement's. The caller of a guard needs this to pick the right
     * {@code *NotFoundException}; nothing about the rule itself depends on it.
     */
    public boolean isPlantVisible(User caller, Plant plant) {
        return plantAccessPolicy.isVisible(caller, plant);
    }

    private UUID plantIdOf(Plant plant) {
        return plant != null ? plant.getId() : null;
    }

    private UUID communityOf(Plant plant) {
        return plantAccessPolicy.communityOf(plant);
    }
}
