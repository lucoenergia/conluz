package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.email.EmailProperties;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationSendingTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-02T00:00:00Z");
    private static final String CONTACT_EMAIL = "member@example.org";
    private static final String TOKEN = "raw-token-value";
    private static final String COMMUNITY_NAME = "Solar Street";

    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private CreateOneTimeTokenService createOneTimeTokenService;
    @Mock
    private GetOneTimeTokenService getOneTimeTokenService;
    @Mock
    private UpdateMembershipInvitationRepository updateMembershipInvitationRepository;
    @Mock
    private EmailSender emailSender;

    private InvitationSending sending() {
        return sending("https://web.test", true);
    }

    private InvitationSending sending(String publicUrl, boolean emailEnabled) {
        return new InvitationSending(lockUserRepository, lockMembershipRepository, getMembershipsRepository,
                createOneTimeTokenService, getOneTimeTokenService, updateMembershipInvitationRepository, emailSender,
                new InvitationEmailFactory(messages()), InvitationLink.of(publicUrl),
                new EmailProperties(emailEnabled, "smtp.test", "587", null, null, "no-reply@conluz.test", null,
                        true),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- requireAvailable ---

    @Test
    void whenTheServerCanSend_nothingIsRefused() {
        assertDoesNotThrow(() -> sending().requireAvailable());
    }

    @Test
    @DisplayName("MEM-009 without a public web URL sending is refused, with one warning naming only the category")
    void withoutAPublicUrl_isRefused() {
        try (LogCapture logs = LogCapture.of(InvitationSending.class)) {
            assertThrows(MembershipInvitationUnavailableException.class, () -> sending("", true).requireAvailable());

            assertThat(logs.all()).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("Email INVITATION not sent: the public web URL is not configured");
        }
        verifyNoInteractions(lockUserRepository, lockMembershipRepository, getMembershipsRepository,
                createOneTimeTokenService, updateMembershipInvitationRepository, emailSender);
    }

    @Test
    @DisplayName("MEM-009 with email sending disabled sending is refused, with one warning naming only the category")
    void withEmailSendingDisabled_isRefused() {
        try (LogCapture logs = LogCapture.of(InvitationSending.class)) {
            assertThrows(MembershipInvitationUnavailableException.class,
                    () -> sending("https://web.test", false).requireAvailable());

            assertThat(logs.all()).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("Email INVITATION not sent: sending is disabled");
        }
        verifyNoInteractions(lockUserRepository, lockMembershipRepository, getMembershipsRepository,
                createOneTimeTokenService, updateMembershipInvitationRepository, emailSender);
    }

    // --- lockAndFind / lockAndRead ---

    @Test
    void theUserRowIsLocked_thenTheMembershipRow_thenTheMembershipIsRead() {
        CommunityMembership stored = membership(new CommunityMembership.Builder());
        locksSucceed();
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.of(stored));

        assertThat(sending().lockAndFind(COMMUNITY_ID, UserId.of(USER_ID))).containsSame(stored);

        InOrder order = inOrder(lockUserRepository, lockMembershipRepository, getMembershipsRepository);
        order.verify(lockUserRepository).lock(UserId.of(USER_ID));
        order.verify(lockMembershipRepository).lock(UserId.of(USER_ID), COMMUNITY_ID);
        order.verify(getMembershipsRepository).findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID);
    }

    @Test
    void anUnknownUser_isFoundEmpty_withoutLockingAMembership() {
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(false);

        assertThat(sending().lockAndFind(COMMUNITY_ID, UserId.of(USER_ID))).isEmpty();

        verifyNoInteractions(lockMembershipRepository, getMembershipsRepository);
    }

    @Test
    void aUserWithoutAMembershipThere_isFoundEmpty_withoutReadingIt() {
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(USER_ID), COMMUNITY_ID)).thenReturn(false);

        assertThat(sending().lockAndFind(COMMUNITY_ID, UserId.of(USER_ID))).isEmpty();

        verifyNoInteractions(getMembershipsRepository);
    }

    @Test
    void lockAndRead_returnsTheMembershipReadUnderTheLock() {
        CommunityMembership stored = membership(new CommunityMembership.Builder());
        locksSucceed();
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.of(stored));

        assertThat(sending().lockAndRead(COMMUNITY_ID, UserId.of(USER_ID))).isSameAs(stored);
    }

    @Test
    void lockAndRead_refusesAMembershipThatDoesNotExist() {
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(USER_ID), COMMUNITY_ID)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class,
                () -> sending().lockAndRead(COMMUNITY_ID, UserId.of(USER_ID)));
    }

    // --- issueAndSend ---

    @Test
    @DisplayName("MEM-010 a send issues an invitation token, records now as the invitation instant, then emails the "
            + "link to the contact email the record has once invited")
    void issueAndSend_issuesRecordsAndEmails_inThatOrder() {
        CommunityMembership locked = membership(new CommunityMembership.Builder()
                .withContactEmail("previous@example.org"));
        CommunityMembership invited = membership(new CommunityMembership.Builder()
                .withContactEmail(CONTACT_EMAIL).withInvitedAt(NOW));
        when(createOneTimeTokenService.issueForMembership(UserId.of(USER_ID), COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION)).thenReturn(new RawOneTimeToken(TOKEN));
        when(updateMembershipInvitationRepository.markInvited(UserId.of(USER_ID), COMMUNITY_ID, NOW))
                .thenReturn(invited);

        assertThat(sending().issueAndSend(locked)).isSameAs(invited);

        InOrder order = inOrder(createOneTimeTokenService, updateMembershipInvitationRepository, emailSender);
        order.verify(createOneTimeTokenService).issueForMembership(UserId.of(USER_ID), COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION);
        order.verify(updateMembershipInvitationRepository).markInvited(UserId.of(USER_ID), COMMUNITY_ID, NOW);
        ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
        order.verify(emailSender).send(email.capture());
        assertThat(email.getValue().recipient()).isEqualTo(CONTACT_EMAIL);
        assertThat(email.getValue().category()).isEqualTo(InvitationEmailFactory.CATEGORY);
    }

    @Test
    @DisplayName("MEM-009 the email names the community and carries the link, with the token in its fragment")
    void issueAndSend_emailsTheLinkWithTheToken() {
        CommunityMembership locked = membership(new CommunityMembership.Builder().withContactEmail(CONTACT_EMAIL));
        when(createOneTimeTokenService.issueForMembership(UserId.of(USER_ID), COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION)).thenReturn(new RawOneTimeToken(TOKEN));
        when(updateMembershipInvitationRepository.markInvited(UserId.of(USER_ID), COMMUNITY_ID, NOW))
                .thenReturn(locked);

        sending().issueAndSend(locked);

        ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().subject()).contains(COMMUNITY_NAME);
        assertThat(email.getValue().body()).contains(COMMUNITY_NAME)
                .contains("https://web.test/accept-invitation#" + TOKEN);
    }

    // --- withInvitation ---

    @Test
    @DisplayName("MEM-013 the membership is returned with the expiry of its valid invitation link")
    void withInvitation_carriesTheValidLinkExpiry() {
        CommunityMembership membership = membership(new CommunityMembership.Builder());
        Instant expiresAt = NOW.plusSeconds(3600);
        when(getOneTimeTokenService.findValidExpiryForMembership(UserId.of(USER_ID), COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION)).thenReturn(Optional.of(expiresAt));

        assertThat(sending().withInvitation(membership))
                .isEqualTo(new MembershipWithInvitation(membership, expiresAt));
    }

    @Test
    @DisplayName("MEM-013 without a valid invitation link the expiry is null")
    void withInvitation_withoutAValidLink_hasNoExpiry() {
        CommunityMembership membership = membership(new CommunityMembership.Builder());
        when(getOneTimeTokenService.findValidExpiryForMembership(UserId.of(USER_ID), COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION)).thenReturn(Optional.empty());

        assertThat(sending().withInvitation(membership))
                .isEqualTo(new MembershipWithInvitation(membership, null));
    }

    private static MessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        return messages;
    }

    private void locksSucceed() {
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(USER_ID), COMMUNITY_ID)).thenReturn(true);
    }

    private static CommunityMembership membership(CommunityMembership.Builder builder) {
        return builder
                .withUser(new User.Builder().id(USER_ID).build())
                .withCommunity(new Community.Builder().withId(COMMUNITY_ID).withName(COMMUNITY_NAME).build())
                .build();
    }
}
