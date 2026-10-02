package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Which of one owner's supplies a listing may return: either all of them, or only those whose
 * community is in a given set. It carries no rule of its own — the access policy decides the scope,
 * and the service only applies it to the query.
 */
public final class SupplyOwnerScope {

    private static final SupplyOwnerScope ALL = new SupplyOwnerScope(null);

    private final Set<UUID> communityIds;

    private SupplyOwnerScope(Set<UUID> communityIds) {
        this.communityIds = communityIds;
    }

    public static SupplyOwnerScope all() {
        return ALL;
    }

    public static SupplyOwnerScope inCommunities(Set<UUID> communityIds) {
        return new SupplyOwnerScope(Set.copyOf(Objects.requireNonNull(communityIds)));
    }

    public boolean isUnrestricted() {
        return communityIds == null;
    }

    /**
     * @return the communities the listing is restricted to; empty when it is {@linkplain #isUnrestricted()
     * unrestricted}, which is why callers must check that first.
     */
    public Set<UUID> communityIds() {
        return communityIds == null ? Set.of() : communityIds;
    }

    /**
     * Whether the scope admits this supply. A supply with no community is admitted only by an
     * unrestricted scope.
     */
    public boolean includes(Supply supply) {
        if (isUnrestricted()) {
            return true;
        }
        UUID communityId = supply.getCommunity() != null ? supply.getCommunity().getId() : null;
        return communityId != null && communityIds.contains(communityId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplyOwnerScope that)) return false;
        return Objects.equals(communityIds, that.communityIds);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(communityIds);
    }

    @Override
    public String toString() {
        return isUnrestricted() ? "SupplyOwnerScope[all]" : "SupplyOwnerScope" + communityIds;
    }
}
