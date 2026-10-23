package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What the caller may do with one membership. Decided from the caller's own memberships and the
 * two ids on the membership in hand, except {@code canListSupplies}, which needs every membership of
 * the member and is the only rule that may query: once for one membership, once for a whole roster
 * through {@link #assembleAll(User, Collection)}, and not at all if the rule does not ask.
 *
 * <p>{@code canUpdateRole} and {@code canDelete} share the guard {@code canManageMemberships}, which
 * is community-wide rather than per-membership; they are reported separately because they are
 * separate things to offer a user, and because the rule behind them may not stay shared. {@code canEnable} and
 * {@code canDisable} share {@code canChangeMemberState} for the same reason, which is decided on the membership in
 * hand, its role included.</p>
 */
@Component
public class MembershipCapabilitiesAssembler {

    private final AccessPolicies policies;
    private final GetMembershipsRepository getMembershipsRepository;

    public MembershipCapabilitiesAssembler(AccessPolicies policies, GetMembershipsRepository getMembershipsRepository) {
        this.policies = policies;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    /**
     * For one membership. Issues at most one query.
     */
    public MembershipCapabilitiesResponse assemble(User caller, CommunityMembership membership) {
        return assembleAll(caller, List.of(membership)).apply(membership);
    }

    /**
     * For a roster. Issues <strong>one</strong> query for the whole roster, and none at all if no rule ends up
     * asking.
     */
    public Function<CommunityMembership, MembershipCapabilitiesResponse> assembleAll(
            User caller, Collection<CommunityMembership> memberships) {
        Set<UUID> userIds = memberships.stream()
                .map(CommunityMembership::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Supplier<Map<UUID, List<CommunityMembership>>> batch = memoize(() -> userIds.isEmpty()
                ? Map.of()
                : getMembershipsRepository.findByUserIds(userIds));
        return membership -> {
            UUID userId = membership.getUser() != null ? membership.getUser().getId() : null;
            return assemble(caller, membership, () -> batch.get().getOrDefault(userId, List.of()));
        };
    }

    private MembershipCapabilitiesResponse assemble(User caller, CommunityMembership membership,
                                                    Supplier<List<CommunityMembership>> memberMemberships) {
        UUID communityId = membership.getCommunity() != null ? membership.getCommunity().getId() : null;
        UUID userId = membership.getUser() != null ? membership.getUser().getId() : null;
        boolean canManageMemberships = policies.membership()
                .canManageMemberships(caller, communityId).isAllowed();
        boolean canChangeState = policies.membership()
                .canChangeMemberState(caller, communityId, () -> membership).isAllowed();

        return MembershipCapabilitiesResponse.builder()
                .withCanUpdateRole(canManageMemberships)
                .withCanDelete(canManageMemberships)
                .withCanManageInvestment(policies.membership().canManageInvestment(caller, communityId).isAllowed())
                .withCanReadPayback(policies.membership().canReadMembershipPrivateData(caller, communityId, userId).isAllowed())
                .withCanInvite(policies.membership().canInviteMembers(caller, communityId).isAllowed())
                .withCanEnable(canChangeState)
                .withCanDisable(canChangeState)
                .withCanEditRecord(policies.membership().canEditMemberRecords(caller, communityId).isAllowed())
                .withCanListSupplies(userId != null
                        && policies.user().canListSuppliesOf(caller, userId, memberMemberships).isAllowed())
                .build();
    }

    private <T> Supplier<T> memoize(Supplier<T> delegate) {
        Map<Boolean, T> cache = new HashMap<>();
        return () -> cache.computeIfAbsent(Boolean.TRUE, key -> delegate.get());
    }
}
