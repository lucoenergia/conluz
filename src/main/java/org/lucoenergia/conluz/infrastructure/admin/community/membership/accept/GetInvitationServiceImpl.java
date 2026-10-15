package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.GetInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationInspection;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.InvitationAttempt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetInvitationServiceImpl implements GetInvitationService {

    private final AuthenticationThrottleService authenticationThrottleService;
    private final InvitationTokenResolver invitationTokenResolver;
    private final GetCommunityRepository getCommunityRepository;

    public GetInvitationServiceImpl(AuthenticationThrottleService authenticationThrottleService,
                                    InvitationTokenResolver invitationTokenResolver,
                                    GetCommunityRepository getCommunityRepository) {
        this.authenticationThrottleService = authenticationThrottleService;
        this.invitationTokenResolver = invitationTokenResolver;
        this.getCommunityRepository = getCommunityRepository;
    }

    @Override
    public InvitationInspection inspect(String rawToken, String clientIp) {
        try (InvitationAttempt attempt = authenticationThrottleService.startInvitation(clientIp)) {
            // Nothing is written, so nothing is locked
            InvitationTokenResolver.UsableInvitation invitation = invitationTokenResolver.find(rawToken, attempt);
            Community community = getCommunityRepository.findById(invitation.token().communityId())
                    .orElseThrow(() -> invitationTokenResolver.invalid(attempt));
            return new InvitationInspection(community.getId(), community.getName(),
                    invitation.user().getPassword() != null);
        }
    }
}
