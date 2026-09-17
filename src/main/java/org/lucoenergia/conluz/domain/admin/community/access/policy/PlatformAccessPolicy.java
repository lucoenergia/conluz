package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

/**
 * Access rules for platform-wide actions — the ones that used to be written as
 * {@code hasRole('PLATFORM_ADMIN')} directly in {@code @PreAuthorize}.
 *
 * <p>These rules never return {@link AccessDecision#NOT_VISIBLE}, and that is the point: a role
 * check makes no statement about whether any particular object exists, so introducing a visibility
 * gate here would turn today's 403 into a 404 for, say, a community admin calling an update
 * endpoint. Object ids are accepted only so each endpoint's decision has a name and a subject, which
 * is what lets it be reported as a capability on that object.</p>
 */
public class PlatformAccessPolicy {

    /**
     * Administering the platform. The object id, where an endpoint has one, is deliberately unused.
     */
    public AccessDecision canAdministerPlatform(User caller) {
        return CallerMemberships.isPlatformAdmin(caller) ? AccessDecision.ALLOWED : AccessDecision.FORBIDDEN;
    }

    /**
     * Administering the platform, applied to someone other than oneself. Backs the revoke-platform-admin
     * rule, where an admin must not be able to strip their own flag.
     */
    public AccessDecision canAdministerOtherUser(User caller, UUID userId) {
        if (!CallerMemberships.isPlatformAdmin(caller)) {
            return AccessDecision.FORBIDDEN;
        }
        return CallerMemberships.isCurrentUser(caller, userId)
                ? AccessDecision.FORBIDDEN
                : AccessDecision.ALLOWED;
    }
}
