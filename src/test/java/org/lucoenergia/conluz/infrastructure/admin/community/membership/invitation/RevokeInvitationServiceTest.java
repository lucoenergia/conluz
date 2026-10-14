package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.RevokeInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.shared.email.EmailProperties;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeInvitationServiceTest {

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
    private GetOneTimeTokenService getOneTimeTokenService;
    @Mock
    private UpdateMembershipInvitationRepository updateMembershipInvitationRepository;
    @Mock
    private EmailSender emailSender;
    @Mock
    private RevokeOneTimeTokenService revokeOneTimeTokenService;

    /**
     * A server that cannot send: revoking does not depend on it.
     */
    private RevokeInvitationService service() {
        return new RevokeInvitationServiceImpl(new InvitationSending(lockUserRepository, lockMembershipRepository,
                getMembershipsRepository, createOneTimeTokenService, getOneTimeTokenService,
                updateMembershipInvitationRepository, emailSender,
                new InvitationEmailFactory(mock(MessageSource.class)), InvitationLink.of(""),
                new EmailProperties(false, "smtp.test", "587", null, null, "no-reply@conluz.test", null, true),
                Clock.systemUTC()), revokeOneTimeTokenService);
    }

    @Test
    @DisplayName("MEM-011 an accepted membership is refused as an existing one, and nothing is revoked")
    void anAcceptedMembership_isRefused() {
        CommunityMembership stored = CommunityMembershipMother.active()
                .withUser(new User.Builder().id(USER_ID).build())
                .withCommunity(new Community.Builder().withId(COMMUNITY_ID).build())
                .build();
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(USER_ID), COMMUNITY_ID)).thenReturn(true);
        when(getMembershipsRepository.findByUserIdAndCommunityId(USER_ID, COMMUNITY_ID))
                .thenReturn(Optional.of(stored));

        assertThrows(MembershipAlreadyExistsException.class, () -> service().revoke(COMMUNITY_ID, UserId.of(USER_ID)));

        verifyNoInteractions(revokeOneTimeTokenService, emailSender);
    }

    @Test
    void aUserWithoutAMembershipThere_isNotFound() {
        when(lockUserRepository.lock(UserId.of(USER_ID))).thenReturn(true);
        when(lockMembershipRepository.lock(UserId.of(USER_ID), COMMUNITY_ID)).thenReturn(false);

        assertThrows(MembershipNotFoundException.class, () -> service().revoke(COMMUNITY_ID, UserId.of(USER_ID)));

        verifyNoInteractions(revokeOneTimeTokenService, emailSender);
    }
}
