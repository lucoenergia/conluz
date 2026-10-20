package org.lucoenergia.conluz.domain.admin.user.token;

import java.time.Duration;

/**
 * What a one-time token is issued for. A token is only valid for the purpose it was issued for.
 *
 * <p>Each purpose has a {@link OneTimeTokenScope}: what the token is bound to, besides its user. A purpose is used only
 * with the operations of its scope in the token services: any other combination is refused with an
 * {@link IllegalArgumentException}.</p>
 */
public enum OneTimeTokenPurpose {

    PASSWORD_RESET(Duration.ofDays(1), OneTimeTokenScope.USER),
    INVITATION(Duration.ofDays(7), OneTimeTokenScope.MEMBERSHIP),
    PLATFORM_ADMIN_INVITATION(Duration.ofDays(7), OneTimeTokenScope.PLATFORM_ADMIN_INVITATION);

    private final Duration lifetime;
    private final OneTimeTokenScope scope;

    OneTimeTokenPurpose(Duration lifetime, OneTimeTokenScope scope) {
        this.lifetime = lifetime;
        this.scope = scope;
    }

    /**
     * How long a token of this purpose stays valid after it is issued.
     */
    public Duration lifetime() {
        return lifetime;
    }

    /**
     * What a token of this purpose is bound to, besides its user.
     */
    public OneTimeTokenScope scope() {
        return scope;
    }

    /**
     * @throws IllegalArgumentException if this purpose is bound to more than its user
     */
    public void requireBoundOnlyToUser() {
        requireScope(OneTimeTokenScope.USER);
    }

    /**
     * @throws IllegalArgumentException if this purpose is not bound to a membership
     */
    public void requireBoundToMembership() {
        requireScope(OneTimeTokenScope.MEMBERSHIP);
    }

    /**
     * @throws IllegalArgumentException if this purpose is not bound to a platform-admin invitation
     */
    public void requireBoundToPlatformAdminInvitation() {
        requireScope(OneTimeTokenScope.PLATFORM_ADMIN_INVITATION);
    }

    private void requireScope(OneTimeTokenScope expected) {
        if (scope != expected) {
            throw new IllegalArgumentException(name() + " tokens have the scope " + scope + ", not " + expected);
        }
    }
}
