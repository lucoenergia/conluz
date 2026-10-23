package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberDataView;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the member data a response shows about a user (MEM-026), loading the member records it needs.
 *
 * <p>As in {@code UserCapabilitiesAssembler}, the method name says whether it queries: the
 * {@code ...WithLoadedMemberships} method reads what the user carries and issues no query, and the others issue
 * <strong>one</strong> query, for a whole page when given one.</p>
 */
@Component
public class MemberDataAssembler {

    private final AccessPolicies policies;
    private final GetMembershipsRepository getMembershipsRepository;

    public MemberDataAssembler(AccessPolicies policies, GetMembershipsRepository getMembershipsRepository) {
        this.policies = policies;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    /**
     * The owner's member record in the supply's community, or {@code null} when the supply has no owner or its owner
     * has no membership in that community.
     */
    public CommunityMembership recordOfSupplyOwner(Supply supply) {
        return recordsOfSupplyOwners(List.of(supply)).apply(supply);
    }

    /**
     * Each owner's member record in their supply's community, read in one query for all the supplies. The function
     * answers {@code null} for a supply with no owner, or whose owner has no membership in its community.
     */
    public Function<Supply, CommunityMembership> recordsOfSupplyOwners(Collection<Supply> supplies) {
        Set<UUID> ownerIds = supplies.stream()
                .map(Supply::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .collect(Collectors.toSet());
        Set<UUID> communityIds = supplies.stream()
                .map(supply -> supply.getCommunity().getId())
                .collect(Collectors.toSet());
        // Every owner in every community of the page: a superset of the pairs needed, narrowed by the key below.
        Map<OwnerInCommunity, CommunityMembership> records =
                getMembershipsRepository.findByUserIdsAndCommunityIds(ownerIds, communityIds).stream()
                        .collect(Collectors.toMap(
                                m -> new OwnerInCommunity(m.getUser().getId(), m.getCommunity().getId()),
                                Function.identity()));
        return supply -> supply.getUser() == null ? null
                : records.get(new OwnerInCommunity(supply.getUser().getId(), supply.getCommunity().getId()));
    }

    /**
     * What the caller sees about a user who carries all their memberships. Issues no query.
     */
    public MemberDataView forUserWithLoadedMemberships(User caller, User target) {
        return policies.memberData().forUser(caller, target, target::getMemberships);
    }

    private record OwnerInCommunity(UUID userId, UUID communityId) {
    }
}
