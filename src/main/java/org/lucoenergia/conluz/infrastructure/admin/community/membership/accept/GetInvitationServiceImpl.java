package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.GetInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationInspection;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.InvitationAttempt;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.GetPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitation;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPlatformAdminInvitation;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetInvitationServiceImpl implements GetInvitationService {

    private final AuthenticationThrottleService authenticationThrottleService;
    private final InvitationTokenResolver invitationTokenResolver;
    private final GetCommunityRepository getCommunityRepository;
    private final GetOneTimeTokenService getOneTimeTokenService;
    private final GetUserRepository getUserRepository;
    private final GetPlatformAdminInvitationRepository getPlatformAdminInvitationRepository;

    public GetInvitationServiceImpl(AuthenticationThrottleService authenticationThrottleService,
                                    InvitationTokenResolver invitationTokenResolver,
                                    GetCommunityRepository getCommunityRepository,
                                    GetOneTimeTokenService getOneTimeTokenService,
                                    GetUserRepository getUserRepository,
                                    GetPlatformAdminInvitationRepository getPlatformAdminInvitationRepository) {
        this.authenticationThrottleService = authenticationThrottleService;
        this.invitationTokenResolver = invitationTokenResolver;
        this.getCommunityRepository = getCommunityRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.getUserRepository = getUserRepository;
        this.getPlatformAdminInvitationRepository = getPlatformAdminInvitationRepository;
    }

    @Override
    public InvitationInspection inspect(String rawToken, String clientIp) {
        try (InvitationAttempt attempt = authenticationThrottleService.startInvitation(clientIp)) {
            // Nothing is written, so nothing is locked
            Optional<OneTimeTokenPlatformAdminInvitation> platformAdminInvitation = getOneTimeTokenService
                    .findPlatformAdminInvitation(rawToken, OneTimeTokenPurpose.PLATFORM_ADMIN_INVITATION);
            if (platformAdminInvitation.isPresent()) {
                return inspect(platformAdminInvitation.get(), attempt);
            }
            // Anything else goes the community way, which refuses a token that is not a community invitation's either
            InvitationTokenResolver.UsableInvitation invitation = invitationTokenResolver.find(rawToken, attempt);
            Community community = getCommunityRepository.findById(invitation.token().communityId())
                    .orElseThrow(() -> invitationTokenResolver.invalid(attempt));
            return new InvitationInspection(community.getId(), community.getName(),
                    invitation.user().getPassword() != null);
        }
    }

    /**
     * A platform-admin invitation is usable while it is pending, whether or not its account is enabled: a disabled
     * account may accept it, and is granted the role without being able to log in (ADR-0004).
     */
    private InvitationInspection inspect(OneTimeTokenPlatformAdminInvitation token, InvitationAttempt attempt) {
        Optional<PlatformAdminInvitation> invitation = getPlatformAdminInvitationRepository
                .findById(token.invitationId()).filter(PlatformAdminInvitation::isPending);
        Optional<User> user = getUserRepository.findById(token.userId());
        if (invitation.isEmpty() || user.isEmpty()) {
            throw invitationTokenResolver.invalid(attempt);
        }
        return InvitationInspection.platform(user.get().getPassword() != null);
    }
}
