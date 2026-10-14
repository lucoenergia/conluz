package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyInvitedException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendInvitationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COMMUNITY_ID = UUID.randomUUID();

    @Mock
    private LockUserRepository lockUserRepository;
    @Mock
    private LockMembershipRepository lockMembershipRepository;
    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private CreateOneTimeTokenService createOneTimeTokenService;
    @Mock
    private UpdateMembershipInvitationRepository updateMembershipInvitationRepository;
    @Mock
    private EmailSender emailSender;

    private SendInvitationService service() {
        return new SendInvitationServiceImpl(lockUserRepository, lockMembershipRepository, getMembershipsRepository,
                createOneTimeTokenService, updateMembershipInvitationRepository, emailSender,
                new InvitationEmailFactory(mock(MessageSource.class)), InvitationLink.of("https://web.test"),
                Clock.systemUTC());
    }

    @Test
    @DisplayName("MEM-009 an accepted membership is refused as an existing one, and nothing is issued or sent")
    void anAcceptedMembership_isRefused() {
        storedUnderTheLock(CommunityMembershipMother.active().withContactEmail("member@example.org"));

        assertThrows(MembershipAlreadyExistsException.class, () -> service().send(COMMUNITY_ID, UserId.of(USER_ID)));

        nothingIsIssuedOrSent();
    }

    @Test
    @DisplayName("MEM-009 a membership already invited is refused, and nothing is issued or sent")
    void anInvitedMembership_isRefused() {
        storedUnderTheLock(new CommunityMembership.Builder().withContactEmail("member@example.org")
                .withInvitedAt(Instant.parse("2026-01-01T00:00:00Z")));

        assertThrows(MembershipAlreadyInvitedException.class,
                () -> service().send(COMMUNITY_ID, UserId.of(USER_ID)));

        nothingIsIssuedOrSent();
    }

    @Test
    @DisplayName("MEM-009 a member record without a contact email is refused, and nothing is issued or sent")
    void aRecordWithoutAContactEmail_isRefused() {
        storedUnderTheLock(new CommunityMembership.Builder());

        assertThrows(MembershipWithoutContactEmailException.class,
                () -> service().send(COMMUNITY_ID, UserId.of(USER_ID)));

        nothingIsIssuedOrSent();
    }

    @Test
    void aUserWithoutAMembershipThere_isNotFound() {
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(USER_ID), COMMUNITY_ID)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().send(COMMUNITY_ID, UserId.of(USER_ID)));

        nothingIsIssuedOrSent();
    }

    @Test
    void anUnknownUser_isNotFound() {
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().send(COMMUNITY_ID, UserId.of(USER_ID)));

        verifyNoInteractions(lockMembershipRepository);
        nothingIsIssuedOrSent();
    }

    /**
     * The state that decides is the one read once both rows are locked.
     */
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

    private void nothingIsIssuedOrSent() {
        verifyNoInteractions(createOneTimeTokenService, updateMembershipInvitationRepository, emailSender);
    }
}
