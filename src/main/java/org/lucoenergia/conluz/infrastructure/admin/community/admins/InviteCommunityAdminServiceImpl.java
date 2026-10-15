package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.admins.CommunityAdmin;
import org.lucoenergia.conluz.domain.admin.community.admins.InviteCommunityAdminService;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisteredMember;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationSending;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.MembershipRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class InviteCommunityAdminServiceImpl implements InviteCommunityAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InviteCommunityAdminServiceImpl.class);

    private final MembershipRegistration registration;
    private final InvitationSending sending;

    public InviteCommunityAdminServiceImpl(MembershipRegistration registration, InvitationSending sending) {
        this.registration = registration;
        this.sending = sending;
    }

    @Override
    public CommunityAdmin invite(UUID communityId, UserId invitedBy, UserPersonalId personalId, MemberRecord record) {
        // Before anything is created: an admin who is registered but cannot be sent the invitation would be left
        // pending with no way in
        sending.requireAvailable();

        RegisteredMember registered = registration.register(communityId, invitedBy, personalId, record,
                CommunityRole.COMMUNITY_ADMIN);
        UserId userId = UserId.of(registered.membership().getUser().getId());

        // The registration holds the user row already; the membership row is locked next, as the first send does
        CommunityMembership membership = sending.lockAndRead(communityId, userId);
        CommunityMembership invited = sending.issueAndSend(membership);

        LOGGER.info("Community admin invited: userId={}, communityId={}", userId.getId(), communityId);
        return new CommunityAdmin(registered.personalId(), invited,
                sending.withInvitation(invited).invitationExpiresAt());
    }
}
