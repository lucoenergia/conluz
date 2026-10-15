package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipDisabledException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationResendLimitException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotInvitedException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The rules of resending an invitation (ADR-0003 §5), shared by the community admin resending a member's invitation
 * and the platform admin resending a community admin's: a new link, at most 4 sends per membership in any 24 hours.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class InvitationResending {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationResending.class);

    /**
     * One initial send plus at most 3 resends per membership in any 24 hours (ADR-0003 §5).
     */
    static final int MAX_SENDS_PER_WINDOW = 4;
    static final Duration WINDOW = Duration.ofHours(24);

    private final InvitationSending sending;
    private final GetOneTimeTokenService getOneTimeTokenService;
    private final Clock clock;

    public InvitationResending(InvitationSending sending, GetOneTimeTokenService getOneTimeTokenService,
                               Clock clock) {
        this.sending = sending;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.clock = clock;
    }

    /**
     * @param inScope whether the membership read under the lock is one the caller's endpoint acts on; one that is not
     *                is answered as a missing one
     * @return the membership, with its new invitation instant
     */
    public CommunityMembership resend(UUID communityId, UserId userId, Predicate<CommunityMembership> inScope) {
        sending.requireAvailable();

        CommunityMembership membership = sending.lockAndRead(communityId, userId);
        if (!inScope.test(membership)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        if (!membership.isPending()) {
            throw new MembershipAlreadyExistsException(communityId, userId.getId());
        }
        // Pending but disabled: checked under the lock, so a membership disabled meanwhile is not sent a link
        if (!membership.isAwaitingAcceptance()) {
            throw new MembershipDisabledException(communityId, userId.getId());
        }
        if (membership.getInvitedAt() == null) {
            throw new MembershipNotInvitedException(communityId, userId.getId());
        }
        if (membership.getContactEmail() == null) {
            throw new MembershipWithoutContactEmailException(communityId, userId.getId());
        }
        // Counted under the membership's lock, so two concurrent resends at the limit cannot both pass
        Instant now = clock.instant();
        Instant since = now.minus(WINDOW);
        long sent = getOneTimeTokenService.countIssuedForMembershipSince(userId, communityId,
                OneTimeTokenPurpose.INVITATION, since);
        if (sent >= MAX_SENDS_PER_WINDOW) {
            long retryAfterSeconds = retryAfterSeconds(userId, communityId, since, now);
            LOGGER.info("Member invitation resend refused: userId={}, communityId={}, retryAfterSeconds={}",
                    userId.getId(), communityId, retryAfterSeconds);
            throw new MembershipInvitationResendLimitException(communityId, userId.getId(), retryAfterSeconds);
        }
        return sending.issueAndSend(membership);
    }

    /**
     * The whole seconds until the oldest counted send is more than {@link #WINDOW} old, which brings the count below
     * the limit. Every invitation after the first is issued here, under the lock and below the limit, so the count
     * never exceeds it and the oldest counted send is the one that has to leave. A send exactly {@link #WINDOW} old
     * still counts, so a whole number of seconds is followed by one more.
     */
    private long retryAfterSeconds(UserId userId, UUID communityId, Instant since, Instant now) {
        Instant oldest = getOneTimeTokenService.findEarliestIssuedForMembershipSince(userId, communityId,
                OneTimeTokenPurpose.INVITATION, since).orElseThrow();
        return Duration.between(now, oldest.plus(WINDOW)).getSeconds() + 1;
    }
}
