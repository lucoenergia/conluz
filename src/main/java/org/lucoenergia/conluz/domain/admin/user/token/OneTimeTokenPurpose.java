package org.lucoenergia.conluz.domain.admin.user.token;

import java.time.Duration;

/**
 * What a one-time token is issued for. A token is only valid for the purpose it was issued for.
 */
public enum OneTimeTokenPurpose {

    PASSWORD_RESET(Duration.ofDays(1));

    private final Duration lifetime;

    OneTimeTokenPurpose(Duration lifetime) {
        this.lifetime = lifetime;
    }

    /**
     * How long a token of this purpose stays valid after it is issued.
     */
    public Duration lifetime() {
        return lifetime;
    }
}
