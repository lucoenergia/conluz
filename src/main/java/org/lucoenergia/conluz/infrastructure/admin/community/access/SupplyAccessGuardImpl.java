package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.SupplyAccessGuard;
import org.lucoenergia.conluz.domain.admin.community.access.policy.AccessDecision;
import org.lucoenergia.conluz.domain.admin.community.access.policy.SupplyAccessPolicy;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.UUID;

/**
 * Adapter over {@link SupplyAccessPolicy}: resolves the caller and the supply, then turns the
 * policy's decision into this layer's vocabulary — {@code NOT_VISIBLE} into
 * {@link SupplyNotFoundException} (404), {@code FORBIDDEN} into {@code false} (403).
 */
class SupplyAccessGuardImpl implements SupplyAccessGuard {

    private final CommunityAccessGuardHelper helper;
    private final GetSupplyRepository getSupplyRepository;
    private final SupplyAccessPolicy policy;

    public SupplyAccessGuardImpl(CommunityAccessGuardHelper helper, GetSupplyRepository getSupplyRepository) {
        this.helper = helper;
        this.getSupplyRepository = getSupplyRepository;
        this.policy = new SupplyAccessPolicy();
    }

    @Override
    public boolean canReadSupply(UUID supplyId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null || supplyId == null) {
            return false;
        }
        return resolve(policy.canRead(user, findSupply(supplyId)), supplyId);
    }

    @Override
    public boolean canEditSupply(UUID supplyId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null || supplyId == null) {
            return false;
        }
        return resolve(policy.canEdit(user, findSupply(supplyId)), supplyId);
    }

    @Override
    public boolean canReadSupplyPartitionCoefficients(UUID supplyId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null || supplyId == null) {
            return false;
        }
        return resolve(policy.canReadPartitionCoefficients(user, findSupply(supplyId)), supplyId);
    }

    @Override
    public boolean isCommunityAdminOfSupply(UUID supplyId) {
        User user = helper.getCurrentUser().orElse(null);
        if (user == null || supplyId == null) {
            return false;
        }
        // Never throws: the policy has no not-visible outcome for this question.
        return policy.isCommunityAdminOfSupply(user, findSupply(supplyId)).isAllowed();
    }

    private boolean resolve(AccessDecision decision, UUID supplyId) {
        if (decision == AccessDecision.NOT_VISIBLE) {
            throw new SupplyNotFoundException(SupplyId.of(supplyId));
        }
        return decision.isAllowed();
    }

    private Supply findSupply(UUID supplyId) {
        return getSupplyRepository.findById(SupplyId.of(supplyId)).orElse(null);
    }
}
