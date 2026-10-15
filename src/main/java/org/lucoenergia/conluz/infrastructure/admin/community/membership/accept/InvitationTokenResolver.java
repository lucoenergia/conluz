package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationTokenInvalidException;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.InvitationAttempt;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenMembership;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The checks every invitation endpoint applies to a token before doing anything with it (#394). A token is usable
 * when it is a valid invitation token whose membership is still pending and whose account is enabled; whether the
 * membership is enabled does not matter, since accepting a disabled membership is allowed (ADR-0003, "What states
 * mean"). Every other case is refused the same way, counted against the client address.
 * <p>
 * Runs in the caller's transaction, so that the locks it takes are held until the caller has written.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class InvitationTokenResolver {

    private final GetOneTimeTokenService getOneTimeTokenService;
    private final LockUserRepository lockUserRepository;
    private final LockMembershipRepository lockMembershipRepository;
    private final GetUserRepository getUserRepository;
    private final GetMembershipsRepository getMembershipsRepository;

    public InvitationTokenResolver(GetOneTimeTokenService getOneTimeTokenService,
                                   LockUserRepository lockUserRepository,
                                   LockMembershipRepository lockMembershipRepository,
                                   GetUserRepository getUserRepository,
                                   GetMembershipsRepository getMembershipsRepository) {
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
        this.getUserRepository = getUserRepository;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    /**
     * Finds the token's membership and account without locking anything, for a flow that writes nothing.
     */
    public UsableInvitation find(String rawToken, InvitationAttempt attempt) {
        OneTimeTokenMembership token = findToken(rawToken, attempt);
        return read(token, attempt);
    }

    /**
     * Finds the token's membership without locking, then locks the user row and the membership row, in the order
     * of {@link org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenService}, and reads both again
     * under the locks, so that a membership accepted or an account disabled meanwhile is seen as such. The token
     * itself is left for the caller to consume.
     */
    public UsableInvitation lockAndFind(String rawToken, InvitationAttempt attempt) {
        OneTimeTokenMembership token = findToken(rawToken, attempt);
        if (!lockUserRepository.lock(token.userId())
                || !lockMembershipRepository.lock(token.userId(), token.communityId())) {
            throw invalid(attempt);
        }
        return read(token, attempt);
    }

    /**
     * Counts the unusable token against the client address and returns the exception that refuses it.
     */
    public InvitationTokenInvalidException invalid(InvitationAttempt attempt) {
        attempt.failed();
        return new InvitationTokenInvalidException();
    }

    private OneTimeTokenMembership findToken(String rawToken, InvitationAttempt attempt) {
        return getOneTimeTokenService.findMembership(rawToken, OneTimeTokenPurpose.INVITATION)
                .orElseThrow(() -> invalid(attempt));
    }

    private UsableInvitation read(OneTimeTokenMembership token, InvitationAttempt attempt) {
        Optional<User> user = getUserRepository.findById(token.userId());
        if (user.isEmpty() || !user.get().isEnabled()) {
            throw invalid(attempt);
        }
        Optional<CommunityMembership> membership = getMembershipsRepository
                .findByUserIdAndCommunityId(token.userId().getId(), token.communityId());
        if (membership.isEmpty() || !membership.get().isPending()) {
            throw invalid(attempt);
        }
        return new UsableInvitation(token, user.get(), membership.get());
    }

    /**
     * A usable invitation token: the token's membership as the token store returned it, and the account and the
     * membership as read.
     */
    public record UsableInvitation(OneTimeTokenMembership token, User user, CommunityMembership membership) {
    }
}
