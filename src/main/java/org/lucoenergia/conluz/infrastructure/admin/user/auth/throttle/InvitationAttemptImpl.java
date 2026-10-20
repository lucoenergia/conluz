package org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.InvitationAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InvitationAttemptImpl implements InvitationAttempt {

    // The throttling logs under one category, whichever class writes the line
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationThrottleServiceImpl.class);

    private final ReservedSlot slot;
    private final FailedAttemptCounter accountCounter;
    private final String clientIp;

    InvitationAttemptImpl(ReservedSlot slot, FailedAttemptCounter accountCounter, String clientIp) {
        this.slot = slot;
        this.accountCounter = accountCounter;
        this.clientIp = clientIp;
    }

    @Override
    public void failed() {
        LOGGER.warn("Refused invitation token: ip={}, reason=INVALID_TOKEN", clientIp);
        slot.failed();
    }

    @Override
    public void failedConfirmationCode() {
        LOGGER.warn("Refused invitation token: ip={}, reason=WRONG_CONFIRMATION_CODE", clientIp);
        slot.failed();
    }

    @Override
    public void succeeded(User user) {
        // The slot was reserved before the token told whose account this is, so the account is reset here rather
        // than through the slot
        accountCounter.reset(AuthenticationThrottleServiceImpl.accountOf(user.getPersonalId()));
        slot.succeeded();
    }

    @Override
    public void close() {
        slot.release();
    }
}
