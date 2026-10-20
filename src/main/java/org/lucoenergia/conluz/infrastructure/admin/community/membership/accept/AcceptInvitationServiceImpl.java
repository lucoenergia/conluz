package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.admin.community.membership.accept.AcceptInvitationService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.InvitationAttempt;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeAttemptsExhaustedException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCodeInvalidException;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation.PlatformAdminInvitationAcceptance;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation.PlatformAdminInvitationAcceptance.Outcome;
import org.springframework.stereotype.Service;

/**
 * Accepts the invitation a link was sent for: a community invitation (#394) or a platform-admin invitation
 * (ADR-0004 §5). Runs outside any transaction: each kind is accepted in a transaction of its own, so that the failed
 * code attempts of a platform-admin invitation are committed before the refusal is thrown.
 *
 * <p>A token that is not a valid platform-admin invitation token goes the community way, exactly as before, which
 * refuses it as unusable when it is not a community invitation token either.</p>
 */
@Service
public class AcceptInvitationServiceImpl implements AcceptInvitationService {

    private final AuthenticationThrottleService authenticationThrottleService;
    private final GetOneTimeTokenService getOneTimeTokenService;
    private final CommunityInvitationAcceptance communityInvitationAcceptance;
    private final PlatformAdminInvitationAcceptance platformAdminInvitationAcceptance;

    public AcceptInvitationServiceImpl(AuthenticationThrottleService authenticationThrottleService,
                                       GetOneTimeTokenService getOneTimeTokenService,
                                       CommunityInvitationAcceptance communityInvitationAcceptance,
                                       PlatformAdminInvitationAcceptance platformAdminInvitationAcceptance) {
        this.authenticationThrottleService = authenticationThrottleService;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.communityInvitationAcceptance = communityInvitationAcceptance;
        this.platformAdminInvitationAcceptance = platformAdminInvitationAcceptance;
    }

    @Override
    public void acceptWithNewPassword(String rawToken, String newPassword, String confirmationCode, String clientIp) {
        try (InvitationAttempt attempt = authenticationThrottleService.startInvitation(clientIp)) {
            if (isPlatformAdminInvitation(rawToken)) {
                answer(platformAdminInvitationAcceptance.acceptWithNewPassword(rawToken, confirmationCode,
                        newPassword, attempt));
            } else {
                communityInvitationAcceptance.acceptWithNewPassword(rawToken, newPassword, attempt);
            }
        }
    }

    @Override
    public void acceptAsUser(UserId caller, String rawToken, String confirmationCode, String clientIp) {
        try (InvitationAttempt attempt = authenticationThrottleService.startInvitation(clientIp)) {
            if (isPlatformAdminInvitation(rawToken)) {
                answer(platformAdminInvitationAcceptance.acceptAsUser(caller, rawToken, confirmationCode, attempt));
            } else {
                communityInvitationAcceptance.acceptAsUser(caller, rawToken, attempt);
            }
        }
    }

    private boolean isPlatformAdminInvitation(String rawToken) {
        return getOneTimeTokenService.findPlatformAdminInvitation(rawToken,
                OneTimeTokenPurpose.PLATFORM_ADMIN_INVITATION).isPresent();
    }

    /**
     * Thrown here, once the acceptance's transaction has committed the failed attempt.
     */
    private void answer(Outcome outcome) {
        switch (outcome) {
            case ACCEPTED -> {
            }
            case WRONG_CODE -> throw new ConfirmationCodeInvalidException();
            case ATTEMPTS_EXHAUSTED -> throw new ConfirmationCodeAttemptsExhaustedException();
        }
    }
}
