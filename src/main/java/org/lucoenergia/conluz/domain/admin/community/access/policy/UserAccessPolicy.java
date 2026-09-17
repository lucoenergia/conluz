package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Access rules for a user account.
 *
 * <p>Deciding about a <em>target</em> user needs that user's memberships, which the caller does not
 * carry. They arrive as a {@link Supplier} rather than a resolved list so the branches that do not
 * need them — a platform admin, or a caller asking about themselves — never pay for the lookup, as
 * they do not today. A capability assembler working over already-loaded users passes
 * {@code () -> user.getMemberships()} and pays nothing either.</p>
 */
public class UserAccessPolicy {

    /**
     * Reading a user is open to anyone who can see them at all: platform admins see everyone, a user
     * sees themselves, and a community admin sees the members of the communities they administer.
     */
    public AccessDecision canRead(User caller, UUID userId,
                                  Supplier<List<CommunityMembership>> targetMemberships) {
        return canSee(caller, userId, targetMemberships)
                ? AccessDecision.ALLOWED
                : AccessDecision.NOT_VISIBLE;
    }

    /**
     * Editing is narrower than reading: seeing a user through the self branch does not by itself
     * confer the right to change them, so an ordinary member editing their own record is forbidden
     * rather than allowed. (That is today's behaviour, and this rule reproduces it.)
     */
    public AccessDecision canEdit(User caller, UUID userId,
                                  Supplier<List<CommunityMembership>> targetMemberships) {
        if (!canSee(caller, userId, targetMemberships)) {
            return AccessDecision.NOT_VISIBLE;
        }
        if (CallerMemberships.isPlatformAdmin(caller)) {
            return AccessDecision.ALLOWED;
        }
        return administersACommunityOf(caller, targetMemberships)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Editing someone <em>other</em> than oneself. Backs deleting, enabling and disabling a user,
     * all three of which must be refused for everyone acting on their own account, admins included.
     *
     * <p>The edit decision is settled first and the self check only then, which is the order the
     * SpEL's short-circuiting {@code and} had: a caller who cannot see the target still gets
     * not-visible (→ 404), and a platform admin acting on themselves gets forbidden (→ 403) rather
     * than being told they do not exist.</p>
     */
    public AccessDecision canEditOther(User caller, UUID userId,
                                       Supplier<List<CommunityMembership>> targetMemberships) {
        AccessDecision edit = canEdit(caller, userId, targetMemberships);
        if (edit != AccessDecision.ALLOWED) {
            return edit;
        }
        return CallerMemberships.isCurrentUser(caller, userId)
                ? AccessDecision.FORBIDDEN
                : AccessDecision.ALLOWED;
    }

    /**
     * Listing the supplies of a user: the user themselves, or an enabled community admin of one of
     * their communities.
     *
     * <p>Deliberately <strong>not</strong> {@link #canRead}, which lets every platform admin through.
     * Reading a user is one thing; reading their supplies is reading supply data, and a platform
     * admin who administers none of the user's communities has no access to those supplies when
     * asked for them one by one — {@link SupplyAccessPolicy} answers not-visible. Routing the same
     * data through the user aggregate must not be a way around that.</p>
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
     * Creating a user in a community: any platform admin, or an enabled community admin of that
     * community. The platform-admin branch runs before the community is even considered, so it does
     * not matter whether one was named.
     */
    public AccessDecision canCreateIn(User caller, UUID communityId) {
        if (CallerMemberships.isPlatformAdmin(caller)) {
            return AccessDecision.ALLOWED;
        }
        if (communityId == null) {
            return AccessDecision.FORBIDDEN;
        }
        if (!CallerMemberships.canSeeCommunity(caller, communityId)) {
            return AccessDecision.NOT_VISIBLE;
        }
        return CallerMemberships.hasCommunityAdminRoleIn(caller, communityId)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Listing users: platform admins, and anyone who administers at least one community. Scoping the
     * listing to what they may actually see is the list endpoint's job, not this rule's.
     */
    public AccessDecision canList(User caller) {
        if (CallerMemberships.isPlatformAdmin(caller)) {
            return AccessDecision.ALLOWED;
        }
        return CallerMemberships.hasAnyEnabledCommunityAdminRole(caller)
                ? AccessDecision.ALLOWED
                : AccessDecision.FORBIDDEN;
    }

    /**
     * Whether the caller can <em>see</em> the target user exists. Used to decide between not-found
     * and forbidden.
     */
    public boolean canSee(User caller, UUID userId, Supplier<List<CommunityMembership>> targetMemberships) {
        if (CallerMemberships.isPlatformAdmin(caller)) {
            return true;
        }
        if (caller.getId().equals(userId)) {
            return true;
        }
        return administersACommunityOf(caller, targetMemberships);
    }

    private boolean administersACommunityOf(User caller, Supplier<List<CommunityMembership>> targetMemberships) {
        Set<UUID> targetCommunityIds = CallerMemberships.enabledCommunityIds(targetMemberships.get());
        if (targetCommunityIds.isEmpty()) return false;
        if (caller.getMemberships() == null) return false;
        return caller.getMemberships().stream()
                .anyMatch(m -> targetCommunityIds.contains(m.getCommunity().getId())
                        && m.getRole() == CommunityRole.COMMUNITY_ADMIN
                        && Boolean.TRUE.equals(m.isEnabled()));
    }
}
