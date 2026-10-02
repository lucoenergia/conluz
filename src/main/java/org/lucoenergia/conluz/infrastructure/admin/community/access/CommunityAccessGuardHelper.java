package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.lucoenergia.conluz.domain.admin.community.access.policy.CallerMemberships;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the guard adapters need that a policy cannot provide: the authenticated caller, and the one
 * scope question whose answer lives in the database rather than in the caller's memberships.
 *
 * <p>The membership predicates that used to live here are now
 * {@link CallerMemberships}, so each rule is spelled once and can be evaluated over an
 * already-loaded user.</p>
 */
class CommunityAccessGuardHelper {

    private final AuthService authService;
    private final GetCommunityRepository getCommunityRepository;

    public CommunityAccessGuardHelper(AuthService authService, GetCommunityRepository getCommunityRepository) {
        this.authService = authService;
        this.getCommunityRepository = getCommunityRepository;
    }

    public Optional<User> getCurrentUser() {
        return authService.getCurrentUser();
    }

    /**
     * The communities the user may see. A platform admin sees every community, which is the one
     * membership question that needs a query — everyone else's answer is already in their
     * memberships.
     */
    public Set<UUID> visibleCommunityIds(User user) {
        if (user == null) {
            return Set.of();
        }
        if (CallerMemberships.isPlatformAdmin(user)) {
            return getCommunityRepository.findAllIds();
        }
        return CallerMemberships.enabledMembershipCommunityIds(user);
    }
}
