package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipDisabledException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationResendLimitException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotInvitedException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.ResendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.shared.email.EmailProperties;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendInvitationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant WINDOW_START = NOW.minus(Duration.ofHours(24));

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

    private ResendInvitationService service() {
        return service("https://web.test", true);
    }

    private ResendInvitationService service(String publicUrl, boolean emailEnabled) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InvitationSending sending = new InvitationSending(lockUserRepository, lockMembershipRepository,
                getMembershipsRepository, createOneTimeTokenService, getOneTimeTokenService,
                updateMembershipInvitationRepository, emailSender,
                new InvitationEmailFactory(mock(MessageSource.class)), InvitationLink.of(publicUrl),
                new EmailProperties(emailEnabled, "smtp.test", "587", null, null, "no-reply@conluz.test", null,
                        true),
                clock);
        return new ResendInvitationServiceImpl(new InvitationResending(sending, getOneTimeTokenService, clock),
                sending);
    }

    @Test
    @DisplayName("MEM-010 without a public web URL a resend is refused before anything is locked or issued")
    void withoutAPublicUrl_isRefusedBeforeAnything() {
        assertThrows(MembershipInvitationUnavailableException.class,
                () -> service("", true).resend(COMMUNITY_ID, UserId.of(USER_ID)));

        verifyNoInteractions(lockUserRepository, lockMembershipRepository, getMembershipsRepository);
        nothingIsCountedIssuedOrSent();
    }

    @Test
    @DisplayName("MEM-010 with email sending disabled a resend is refused before anything is locked or issued")
    void withEmailSendingDisabled_isRefusedBeforeAnything() {
        assertThrows(MembershipInvitationUnavailableException.class,
                () -> service("https://web.test", false).resend(COMMUNITY_ID, UserId.of(USER_ID)));

        verifyNoInteractions(lockUserRepository, lockMembershipRepository, getMembershipsRepository);
        nothingIsCountedIssuedOrSent();
    }

    @Test
    @DisplayName("MEM-010 an accepted membership is refused as an existing one before anything is counted")
    void anAcceptedMembership_isRefused() {
        storedUnderTheLock(CommunityMembershipMother.active().withContactEmail("member@example.org")
                .withInvitedAt(NOW.minus(Duration.ofDays(1))));

        assertThrows(MembershipAlreadyExistsException.class,
                () -> service().resend(COMMUNITY_ID, UserId.of(USER_ID)));

        nothingIsCountedIssuedOrSent();
    }

    @Test
    @DisplayName("MEM-025 a disabled pending membership is refused under the lock, and nothing is issued or sent")
    void aDisabledMembership_isRefused() {
        storedUnderTheLock(new CommunityMembership.Builder().withEnabled(false).withContactEmail("member@example.org")
                .withInvitedAt(Instant.parse("2026-01-01T00:00:00Z")));

        assertThrows(MembershipDisabledException.class, () -> service().resend(COMMUNITY_ID, UserId.of(USER_ID)));

        nothingIsCountedIssuedOrSent();
    }

    @Test
    @DisplayName("MEM-010 a membership never invited is refused before anything is counted")
    void aMembershipNeverInvited_isRefused() {
        storedUnderTheLock(new CommunityMembership.Builder().withContactEmail("member@example.org"));

        assertThrows(MembershipNotInvitedException.class, () -> service().resend(COMMUNITY_ID, UserId.of(USER_ID)));

        nothingIsCountedIssuedOrSent();
    }

    @Test
    @DisplayName("MEM-010 a member record without a contact email is refused before anything is counted")
    void aRecordWithoutAContactEmail_isRefused() {
        storedUnderTheLock(new CommunityMembership.Builder().withInvitedAt(NOW.minus(Duration.ofDays(1))));

        assertThrows(MembershipWithoutContactEmailException.class,
                () -> service().resend(COMMUNITY_ID, UserId.of(USER_ID)));

        nothingIsCountedIssuedOrSent();
    }

    @ParameterizedTest(name = "oldest send {0} ago: retry after {1} s")
    @CsvSource({
            "PT20H, 14401",
            "PT20H0.5S, 14400",
            "PT23H59M59S, 2",
            "PT24H, 1",
            "PT1S, 86400"})
    @DisplayName("MEM-010 at the limit, the wait is the whole seconds until the oldest counted send is more than 24 "
            + "hours old")
    void atTheLimit_theWaitIsUntilTheOldestSendLeavesTheWindow(Duration oldestAgo, long expectedRetryAfter) {
        storedUnderTheLock(new CommunityMembership.Builder().withContactEmail("member@example.org")
                .withInvitedAt(NOW.minus(Duration.ofMinutes(1))));
        when(getOneTimeTokenService.countIssuedForMembershipSince(UserId.of(USER_ID), COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION, WINDOW_START)).thenReturn(4L);
        when(getOneTimeTokenService.findEarliestIssuedForMembershipSince(UserId.of(USER_ID), COMMUNITY_ID,
                OneTimeTokenPurpose.INVITATION, WINDOW_START)).thenReturn(Optional.of(NOW.minus(oldestAgo)));

        MembershipInvitationResendLimitException refused = assertThrows(
                MembershipInvitationResendLimitException.class,
                () -> service().resend(COMMUNITY_ID, UserId.of(USER_ID)));

        assertEquals(expectedRetryAfter, refused.getRetryAfterSeconds());
        verifyNoInteractions(createOneTimeTokenService, updateMembershipInvitationRepository, emailSender);
    }

    private void storedUnderTheLock(CommunityMembership.Builder membership) {
        CommunityMembership stored = membership
                .withUser(new User.Builder().id(USER_ID).build())
                .withCommunity(new Community.Builder().withId(COMMUNITY_ID).build())
                .build();
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(USER_ID), COMMUNITY_ID)).thenReturn(true);
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.of(stored));
    }

    private void nothingIsCountedIssuedOrSent() {
        verifyNoInteractions(createOneTimeTokenService, getOneTimeTokenService, updateMembershipInvitationRepository,
                emailSender);
    }
}
