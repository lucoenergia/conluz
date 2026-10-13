package org.lucoenergia.conluz.domain.admin.user.token;

import java.time.Duration;

/**
 * What a one-time token is issued for. A token is only valid for the purpose it was issued for.
 *
 * <p>A purpose is either bound only to a user, with at most one active token per user and purpose, or bound to a
 * membership, with at most one active token per membership whatever its purpose. A user-bound purpose is used only
 * with the user-scoped operations of the token services, and a membership-bound purpose only with the
 * membership-scoped ones: any other combination is refused with an {@link IllegalArgumentException}.</p>
 */
public enum OneTimeTokenPurpose {

    PASSWORD_RESET(Duration.ofDays(1), false),
    INVITATION(Duration.ofDays(7), true);

    private final Duration lifetime;
    private final boolean boundToMembership;

    OneTimeTokenPurpose(Duration lifetime, boolean boundToMembership) {
        this.lifetime = lifetime;
        this.boundToMembership = boundToMembership;
    }

    /**
     * How long a token of this purpose stays valid after it is issued.
     */
    public Duration lifetime() {
        return lifetime;
    }

    /**
     * Whether a token of this purpose is issued for a membership, rather than only for a user.
     */
    public boolean isBoundToMembership() {
        return boundToMembership;
    }

    /**
     * @throws IllegalArgumentException if this purpose is bound to a membership
     */
    public void requireBoundOnlyToUser() {
        if (boundToMembership) {
            throw new IllegalArgumentException(name() + " tokens are bound to a membership, not only to a user");
        }
    }

    /**
     * @throws IllegalArgumentException if this purpose is bound only to a user
     */
    public void requireBoundToMembership() {
        if (!boundToMembership) {
            throw new IllegalArgumentException(name() + " tokens are bound only to a user, not to a membership");
        }
    }
}
