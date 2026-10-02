package org.lucoenergia.conluz.domain.admin.community.access;

import org.lucoenergia.conluz.domain.admin.user.get.UserScope;

import java.util.UUID;

public interface UserAccessGuard {

    boolean canReadUser(UUID userId);

    boolean canEditUser(UUID userId);

    /**
     * Whether the current user may delete the given user: they may edit them, and the target is not
     * themselves. Absorbs the {@code and !isCurrentUser(#userId)} that used to sit in the SpEL, and
     * keeps its evaluation order -- the edit decision is settled first, so a caller who cannot see
     * the target still gets a 404 rather than a 403.
     */
    boolean canDeleteUser(UUID userId);

    /**
     * Whether the current user may enable the given user. Same rule as {@link #canDeleteUser(UUID)};
     * kept separate so each endpoint has its own named decision.
     */
    boolean canEnableUser(UUID userId);

    /**
     * Whether the current user may disable the given user. Same rule as {@link #canDeleteUser(UUID)};
     * kept separate so each endpoint has its own named decision.
     */
    boolean canDisableUser(UUID userId);

    /**
     * Whether the current user may list the supplies of the given user: the user themselves, or an
     * enabled community admin of one of their communities.
     *
     * <p>Deliberately stricter than {@link #canReadUser(UUID)}, which lets every platform admin
     * through. A platform admin who administers none of the user's communities cannot read those
     * supplies one by one, so they must not be able to read them all at once through the user.</p>
     */
    boolean canListSuppliesOfUser(UUID userId);

    boolean canCreateUserIn(UUID communityId);

    boolean canListUsers();

    /**
     * Which users, and which of their memberships, the current user may see, as a scope for a
     * listing query rather than a decision about one object. It does not decide whether the request
     * proceeds — {@code canListUsers} does — it bounds what a permitted request returns, so a listing
     * never carries a user {@link #canReadUser(UUID)} would answer 404 on. Never throws.
     */
    UserScope visibleUsers();
}
