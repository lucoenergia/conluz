package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.UserScope;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Access rules for a user account.
 *
 * <p>Deciding about a <em>target</em> user needs that user's memberships, which the caller does not
 * carry. They arrive as a {@link Supplier} rather than a resolved list so the branches that do not
 * need them — a caller asking about themselves — never pay for the lookup. A capability assembler
 * working over already-loaded users passes {@code () -> user.getMemberships()} and pays nothing
 * either.</p>
 *
 * <p>Being a platform admin opens nothing here (#446): the platform admin reaches another user only
 * through a community it administers, like anyone else. Granting and revoking the platform-admin role
 * are {@link PlatformAccessPolicy}'s rules, not these.</p>
 */
public class UserAccessPolicy {

    /**
     * Reading a user is open to anyone who can see them at all: a user sees themselves, and a
     * community admin sees the members of the communities they administer.
     */
    public AccessDecision canRead(User caller, UUID userId,
                                  Supplier<List<CommunityMembership>> targetMemberships) {
        return canSee(caller, userId, targetMemberships)
                ? AccessDecision.ALLOWED
                : AccessDecision.NOT_VISIBLE;
    }

    /**
     * Acting on the account of someone <em>other</em> than oneself. Backs deleting, enabling and
     * disabling a user, all three of which must be refused for everyone acting on their own account,
     * admins included.
     *
     * <p>Nobody may do it. A community admin may not (#415): the account is shared by every community
     * the person belongs to, so disabling or deleting it would remove them from all of them; a
     * community suspends a member by disabling their membership instead. A platform admin may not
     * either (#446): it has no access to other people's accounts.</p>
     *
     * <p>Visibility is settled first and the refusal only then: a caller who cannot see the target
     * still gets not-visible (→ 404), and a caller who can, themselves included, gets forbidden
     * (→ 403) rather than being told the target does not exist.</p>
     */
    public AccessDecision canEditOther(User caller, UUID userId,
                                       Supplier<List<CommunityMembership>> targetMemberships) {
        if (!canSee(caller, userId, targetMemberships)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return AccessDecision.FORBIDDEN;
    }

    /**
     * Listing the supplies of a user: the user themselves, or an active community admin of one of
     * their communities.
     *
     * <p>Deliberately <strong>not</strong> {@link #canRead}. Reading a user is one thing; reading their
     * supplies is reading supply data, and a community admin who can see the user through one
     * community must not reach supplies elsewhere through the user aggregate. What comes back is
     * scoped by {@link SupplyAccessPolicy#visibleSuppliesOwnedBy}.</p>
     *
     * <p>A caller who can see the user but may not read their supplies is {@code FORBIDDEN}, not
     * not-found: they already know the user exists, so nothing leaks by saying no.</p>
     */
    public AccessDecision canListSuppliesOf(User caller, UUID userId,
                                            Supplier<List<CommunityMembership>> targetMemberships) {
        if (!canSee(caller, userId, targetMemberships)) {
            return AccessDecision.NOT_VISIBLE;
        }
        if (CallerMemberships.isCurrentUser(caller, userId)) {
            return AccessDecision.ALLOWED;
        }
        return administersACommunityOf(caller, targetMemberships)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Listing users: anyone who administers at least one community. Scoping the listing to what they
     * may actually see is the list endpoint's job, not this rule's.
     */
    public AccessDecision canList(User caller) {
        return CallerMemberships.hasAnyActiveCommunityAdminRole(caller)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Whether the caller can <em>see</em> the target user exists: they are the target, or they
     * administer one of the target's communities. Used to decide between not-found and forbidden.
     */
    public boolean canSee(User caller, UUID userId, Supplier<List<CommunityMembership>> targetMemberships) {
        if (caller.getId().equals(userId)) {
            return true;
        }
        return administersACommunityOf(caller, targetMemberships);
    }

    /**
     * {@link #canSee} in set form: which users the caller may see, expressed so a listing can apply it
     * in a single query instead of evaluating each row. Everyone sees themselves and the users with an
     * active membership in a community they administer — never more than they could read one by one.
     * The same scope bounds which of a visible user's memberships a response may carry: those in the
     * caller's administered communities, as {@code GET /communities/{communityId}/memberships} would
     * show them, and all of the caller's own.
     */
    public UserScope visibleUsers(User caller) {
        return UserScope.visibleTo(caller != null ? caller.getId() : null,
                CallerMemberships.adminCommunityIds(caller));
    }

    private boolean administersACommunityOf(User caller, Supplier<List<CommunityMembership>> targetMemberships) {
        Set<UUID> targetCommunityIds = CallerMemberships.activeCommunityIds(targetMemberships.get());
        if (targetCommunityIds.isEmpty()) return false;
        return CallerMemberships.adminCommunityIds(caller).stream().anyMatch(targetCommunityIds::contains);
    }
}
