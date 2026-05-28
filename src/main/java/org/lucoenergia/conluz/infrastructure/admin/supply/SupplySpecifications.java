package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.UUID;

/**
 * JPA Specification helpers for community-scoped supply queries.
 *
 * CONVENTION: All new user-facing supply queries MUST use visibleToCommunities() to prevent
 * cross-community data leaks. The acrossAllCommunities() escape hatch is reserved for
 * system-level operations (batch jobs, admin maintenance). Every call site using
 * acrossAllCommunities() MUST include a comment explaining why it is cross-community.
 */
public class SupplySpecifications {

    private SupplySpecifications() {}

    public static Specification<SupplyEntity> visibleToCommunities(Set<UUID> communityIds) {
        return (root, query, cb) -> {
            if (communityIds == null || communityIds.isEmpty()) {
                return cb.disjunction();
            }
            return root.get("community").get("id").in(communityIds);
        };
    }

    /**
     * Escape hatch — for batch jobs and admin maintenance that legitimately cross community boundaries.
     * Call sites MUST include a code comment justifying cross-community access.
     */
    public static Specification<SupplyEntity> acrossAllCommunities() {
        return (root, query, cb) -> cb.conjunction();
    }
}
