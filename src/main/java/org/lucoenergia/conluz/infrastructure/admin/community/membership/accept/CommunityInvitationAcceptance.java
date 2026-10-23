package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.AdminChangeActor;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChange;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChangeNotifier;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.AcceptMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationLoginRequiredException;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.InvitationAttempt;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordRepository;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicy;
import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenMembership;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserEmailRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailAddresses;
import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Accepts a community invitation (#394), in one transaction under the lock order user row, membership row, token
 * rows. Called by {@link AcceptInvitationServiceImpl} once the token is known to be a community invitation's, or not
 * known at all, with the attempt it started against the client address.
 */
@Component
@Transactional
public class CommunityInvitationAcceptance {

    // The acceptance logs under the category it always has, whichever class writes the line
    private static final Logger LOGGER = LoggerFactory.getLogger(AcceptInvitationServiceImpl.class);

    private final InvitationTokenResolver invitationTokenResolver;
    private final ConsumeOneTimeTokenService consumeOneTimeTokenService;
    private final ChangePasswordRepository changePasswordRepository;
    private final UpdateUserEmailRepository updateUserEmailRepository;
    private final AcceptMembershipRepository acceptMembershipRepository;
    private final UserPasswordEncoder userPasswordEncoder;
    private final GetMembershipsRepository getMembershipsRepository;
    private final CommunityAdminChangeNotifier notifier;
    private final Clock clock;

    public CommunityInvitationAcceptance(InvitationTokenResolver invitationTokenResolver,
                                         ConsumeOneTimeTokenService consumeOneTimeTokenService,
                                         ChangePasswordRepository changePasswordRepository,
                                         UpdateUserEmailRepository updateUserEmailRepository,
                                         AcceptMembershipRepository acceptMembershipRepository,
                                         UserPasswordEncoder userPasswordEncoder,
                                         GetMembershipsRepository getMembershipsRepository,
                                         CommunityAdminChangeNotifier notifier,
                                         Clock clock) {
        this.invitationTokenResolver = invitationTokenResolver;
        this.consumeOneTimeTokenService = consumeOneTimeTokenService;
        this.changePasswordRepository = changePasswordRepository;
        this.updateUserEmailRepository = updateUserEmailRepository;
        this.acceptMembershipRepository = acceptMembershipRepository;
        this.userPasswordEncoder = userPasswordEncoder;
        this.getMembershipsRepository = getMembershipsRepository;
        this.notifier = notifier;
        this.clock = clock;
    }

    public void acceptWithNewPassword(String rawToken, String newPassword, InvitationAttempt attempt) {
        InvitationTokenResolver.UsableInvitation invitation = invitationTokenResolver.lockAndFind(rawToken,
                attempt);
        User user = invitation.user();
        UserId userId = invitation.token().userId();

        // The link never sets or changes an existing password (ADR-0003 §6). Neither this refusal nor the policy
        // check below consumes the token, so the person can try again with the same link
        if (user.getPassword() != null) {
            throw new InvitationLoginRequiredException();
        }
        PasswordPolicy.check(newPassword);

        consume(rawToken, invitation.token(), attempt);

        changePasswordRepository.changePassword(userId, userPasswordEncoder.encode(newPassword), clock.instant());

        // Without an email the person could not recover the password just chosen
        if (user.getEmail() == null) {
            String contactEmail = EmailAddresses.blankToNull(invitation.membership().getContactEmail());
            if (contactEmail != null) {
                updateUserEmailRepository.setEmail(userId, contactEmail);
            }
        }

        accept(invitation, "public");
        attempt.succeeded(user);
    }

    public void acceptAsUser(UserId caller, String rawToken, InvitationAttempt attempt) {
        InvitationTokenResolver.UsableInvitation invitation = invitationTokenResolver.lockAndFind(rawToken,
                attempt);
        // Logging in proves the person, so the token may only accept one of the caller's own memberships
        if (!invitation.token().userId().equals(caller)) {
            throw invitationTokenResolver.invalid(attempt);
        }

        consume(rawToken, invitation.token(), attempt);

        accept(invitation, "authenticated");
    }

    /**
     * Uses up the token, which may have been used or revoked since it was found.
     */
    private void consume(String rawToken, OneTimeTokenMembership found, InvitationAttempt attempt) {
        Optional<OneTimeTokenMembership> consumed = consumeOneTimeTokenService.consumeForMembership(rawToken,
                OneTimeTokenPurpose.INVITATION);
        if (consumed.isEmpty() || !consumed.get().equals(found)) {
            throw invitationTokenResolver.invalid(attempt);
        }
    }

    private void accept(InvitationTokenResolver.UsableInvitation invitation, String endpoint) {
        OneTimeTokenMembership membership = invitation.token();
        acceptMembershipRepository.accept(membership.userId(), membership.communityId(), clock.instant());
        CommunityMembership accepted = getMembershipsRepository
                .findByUserIdAndCommunityId(membership.userId().getId(), membership.communityId())
                .orElseThrow(() -> new MembershipNotFoundException(membership.communityId(),
                        membership.userId().getId()));
        // The person accepting is the one affected; a member's invitation is not a change to the admins
        notifier.notify(CommunityAdminChange.ACCEPTED, invitation.membership(), accepted,
                AdminChangeActor.person(membership.userId()));
        LOGGER.info("Invitation accepted: userId={}, communityId={}, endpoint={}", membership.userId().getId(),
                membership.communityId(), endpoint);
    }
}
