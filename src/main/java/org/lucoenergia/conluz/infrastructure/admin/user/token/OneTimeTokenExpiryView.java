package org.lucoenergia.conluz.infrastructure.admin.user.token;

import java.time.Instant;
import java.util.UUID;

/**
 * The expiry of a membership's valid one-time token, as read by {@link OneTimeTokenJpaRepository}.
 */
public interface OneTimeTokenExpiryView {

    UUID getMembershipId();

    Instant getExpiresAt();
}
