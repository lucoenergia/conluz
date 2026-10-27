package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.AdminChangeActor;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChange;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChangeNotifier;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.ResendInvitationService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ResendInvitationServiceImpl implements ResendInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendInvitationServiceImpl.class);

    private final InvitationResending resending;
    private final InvitationSending sending;
    private final CommunityAdminChangeNotifier notifier;

    public ResendInvitationServiceImpl(InvitationResending resending, InvitationSending sending,
                                       CommunityAdminChangeNotifier notifier) {
        this.resending = resending;
        this.sending = sending;
        this.notifier = notifier;
    }

    @Override
    public MembershipWithInvitation resend(UUID communityId, UserId userId, UserId resentBy) {
        // Every membership of the community, whatever its role
        CommunityMembership invited = resending.resend(communityId, userId, membership -> true);
        // Skipped by the notifier when the membership is a member's
        notifier.notify(CommunityAdminChange.INVITATION_RESENT, null, invited, AdminChangeActor.person(resentBy));

        LOGGER.info("Member invitation resent: userId={}, communityId={}", userId.getId(), communityId);
        return sending.withInvitation(invited);
    }
}
