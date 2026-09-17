package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.PlantAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.access.policy.AccessDecision;
import org.lucoenergia.conluz.domain.admin.community.access.policy.PlantAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.access.policy.SharingAgreementAccessPolicy;
import org.lucoenergia.conluz.domain.admin.community.access.policy.SupplyAccessPolicy;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;

import java.util.UUID;

/**
 * Adapter over {@link PlantAccessPolicy} and {@link SharingAgreementAccessPolicy}. Each method maps
 * {@code NOT_VISIBLE} to the {@code *NotFoundException} of whichever resource must not be leaked —
 * the plant, the supply behind it, or the community — and {@code FORBIDDEN} to {@code false}.
 */
class PlantAccessGuardImpl implements PlantAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final GetPlantRepository getPlantRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final PlantAccessPolicy policy;
    private final SharingAgreementAccessPolicy sharingAgreementPolicy;

    public PlantAccessGuardImpl(CommunityAccessGuardHelper helper, GetPlantRepository getPlantRepository,
                                GetSupplyRepository getSupplyRepository,
                                GetSharingAgreementRepository getSharingAgreementRepository) {
        this.helper = helper;
        this.getPlantRepository = getPlantRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.policy = new PlantAccessPolicy(new SupplyAccessPolicy());
        this.sharingAgreementPolicy = new SharingAgreementAccessPolicy();
    }

    @Override
    public boolean canManagePlant(UUID plantId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolvePlant(policy.canManage(user, findPlant(plantId)), plantId);
    }

    @Override
    public boolean canReadPlant(UUID plantId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        return resolvePlant(policy.canRead(user, findPlant(plantId)), plantId);
    }

    @Override
    public boolean canCreatePlant(String supplyCode) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        if (supplyCode == null) {
            return false;
        }
        Supply supply = getSupplyRepository.findByCode(SupplyCode.of(supplyCode)).orElse(null);
        AccessDecision decision = policy.canCreate(user, supply);
        // The supply, not the plant, is the resource whose existence must not leak here.
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new SupplyNotFoundException(SupplyCode.of(supplyCode));
        }
        return decision.isAllowed();
    }

    @Override
    public boolean canListPlants(UUID communityId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        AccessDecision decision = policy.canList(user, communityId);
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new CommunityNotFoundException(communityId);
        }
        return decision.isAllowed();
    }

    @Override
    public boolean canReadSharingAgreement(UUID plantId, UUID sharingAgreementId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        Plant plant = requireVisiblePlant(user, plantId);
        SharingAgreement agreement = findAgreement(sharingAgreementId);
        return resolveAgreement(sharingAgreementPolicy.canRead(user, plant, agreement), sharingAgreementId);
    }

    @Override
    public boolean canManageSharingAgreement(UUID plantId) {
        // Same precondition as managing the plant itself; kept as a separate method so the
        // @PreAuthorize SpEL at the sharing-agreement endpoints reads correctly and the two rules
        // can diverge later without touching call sites.
        return canManagePlant(plantId);
    }

    @Override
    public boolean canListSharingAgreements(UUID plantId) {
        // The same decision as creating one under this plant, kept as its own method so the listing
        // rule can diverge later without touching call sites.
        return canManagePlant(plantId);
    }

    @Override
    public boolean canManageSharingAgreement(UUID plantId, UUID sharingAgreementId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null) {
            return false;
        }
        Plant plant = requireVisiblePlant(user, plantId);
        SharingAgreement agreement = findAgreement(sharingAgreementId);
        return resolveAgreement(sharingAgreementPolicy.canManage(user, plant, agreement), sharingAgreementId);
    }

    /**
     * Settles the plant before the agreement is even looked up: the two carry different not-found
     * identities, and a caller who cannot see the plant must be told the <em>plant</em> is missing,
     * not the agreement.
     */
    private Plant requireVisiblePlant(User user, UUID plantId) {
        Plant plant = findPlant(plantId);
        if (!policy.isVisible(user, plant)) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        return plant;
    }

    private boolean resolvePlant(AccessDecision decision, UUID plantId) {
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        return decision.isAllowed();
    }

    private boolean resolveAgreement(AccessDecision decision, UUID sharingAgreementId) {
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new SharingAgreementNotFoundException(sharingAgreementId);
        }
        return decision.isAllowed();
    }

    private Plant findPlant(UUID plantId) {
        return getPlantRepository.findById(PlantId.of(plantId)).orElse(null);
    }

    private SharingAgreement findAgreement(UUID sharingAgreementId) {
        return getSharingAgreementRepository.findById(sharingAgreementId).orElse(null);
    }
}
