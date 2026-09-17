package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What the caller may do with a user.
 *
 * <p>Unlike every other assembler here, these rules need something the entity does not carry: the
 * <em>target's</em> memberships, which decide whether the caller administers a community of theirs.
 * Whether a given {@link User} has them attached depends on where it came from —
 * {@code GetUserServiceImpl} and the security principal attach them, and no entity mapper ever
 * does — and it cannot be told from the value: {@code User.memberships} starts life as an empty
 * list, so a user whose memberships were never loaded and one who genuinely belongs to no community
 * look exactly alike. Guessing from emptiness would silently report the wrong capabilities for
 * every user embedded in a supply or a membership.</p>
 *
 * <p>So the choice is made by the caller of this class, through the method name, rather than
 * inferred here. The {@code ...WithLoadedMemberships} methods read what the user carries and issue
 * no query; the {@code ...FetchingMemberships} methods issue <strong>one</strong> batch query for
 * the whole page, never one per item.</p>
 *
 * <p>That batch is used only to decide capabilities. It is deliberately not written back onto the
 * targets: {@code UserResponse.memberships} serialises as {@code {}} for an embedded user today,
 * and filling it in would hand every caller the community memberships of people they can see only
 * as a supply owner.</p>
 */
@Component
public class UserCapabilitiesAssembler {

    private final AccessPolicies policies;
    private final GetMembershipsRepository getMembershipsRepository;

    public UserCapabilitiesAssembler(AccessPolicies policies, GetMembershipsRepository getMembershipsRepository) {
        this.policies = policies;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    /**
     * For a user whose memberships are already attached — the security principal, and anything that
     * came through {@code GetUserService}. Issues no query.
     */
    public UserCapabilitiesResponse assembleWithLoadedMemberships(User caller, User target) {
        return assemble(caller, target.getId(), attachedMembershipsOf(target));
    }

    /**
     * For a page of users whose memberships are already attached, keyed by user id. Issues no query.
     */
    public Map<UUID, UserCapabilitiesResponse> assembleAllWithLoadedMemberships(User caller, List<User> targets) {
        Map<UUID, UserCapabilitiesResponse> byUserId = new LinkedHashMap<>();
        for (User target : targets) {
            if (target != null && target.getId() != null) {
                byUserId.put(target.getId(), assembleWithLoadedMemberships(caller, target));
            }
        }
        return byUserId;
    }

    /**
     * For a user that carries no memberships — one embedded in a supply or a membership. Issues at
     * most one query.
     */
    public UserCapabilitiesResponse assembleFetchingMemberships(User caller, User target) {
        return assembleAllFetchingMemberships(caller, List.of(target)).get(target.getId());
    }

    /**
     * For a page of users that carry no memberships, keyed by user id. Issues <strong>one</strong>
     * query for the whole page, and none at all if no rule ends up asking — a platform admin reading
     * their own record never does.
     */
    public Map<UUID, UserCapabilitiesResponse> assembleAllFetchingMemberships(User caller, List<User> targets) {
        Set<UUID> userIds = targets.stream()
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Supplier<Map<UUID, List<CommunityMembership>>> batch = memoize(() -> userIds.isEmpty()
                ? Map.of()
                : getMembershipsRepository.findByUserIds(userIds));

        Map<UUID, UserCapabilitiesResponse> byUserId = new LinkedHashMap<>();
        for (User target : targets) {
            if (target == null || target.getId() == null) {
                continue;
            }
            UUID userId = target.getId();
            byUserId.put(userId, assemble(caller, userId,
                    () -> batch.get().getOrDefault(userId, List.of())));
        }
        return byUserId;
    }

    private UserCapabilitiesResponse assemble(User caller, UUID userId,
                                              Supplier<List<CommunityMembership>> targetMemberships) {
        return UserCapabilitiesResponse.builder()
                .withCanRead(policies.user().canRead(caller, userId, targetMemberships).isAllowed())
                .withCanEdit(policies.user().canEdit(caller, userId, targetMemberships).isAllowed())
                .withCanDelete(policies.user().canEditOther(caller, userId, targetMemberships).isAllowed())
                .withCanEnable(policies.user().canEditOther(caller, userId, targetMemberships).isAllowed())
                .withCanDisable(policies.user().canEditOther(caller, userId, targetMemberships).isAllowed())
                .withCanGrantPlatformAdmin(policies.platform().canAdministerPlatform(caller).isAllowed())
                .withCanRevokePlatformAdmin(policies.platform().canAdministerOtherUser(caller, userId).isAllowed())
                .withCanListSupplies(policies.user().canListSuppliesOf(caller, userId, targetMemberships).isAllowed())
                .build();
    }

    /**
     * A user's own memberships, defensively copied out of the mutable list the domain object hands
     * back, so a policy cannot be handed something that changes underneath it.
     */
    private Supplier<List<CommunityMembership>> attachedMembershipsOf(User target) {
        List<CommunityMembership> memberships = target.getMemberships() == null
                ? List.of()
                : List.copyOf(target.getMemberships());
        return () -> memberships;
    }

    private <T> Supplier<T> memoize(Supplier<T> delegate) {
        Map<Boolean, T> cache = new HashMap<>();
        return () -> cache.computeIfAbsent(Boolean.TRUE, key -> delegate.get());
    }
}
