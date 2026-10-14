package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
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
class SendInvitationServiceTest {

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
        return new SendInvitationServiceImpl(lockMembershipRepository, getMembershipsRepository,
                createOneTimeTokenService, updateMembershipInvitationRepository, emailSender,
                new InvitationEmailFactory(mock(MessageSource.class)), InvitationLink.of("https://web.test"),
                Clock.systemUTC());
    }

    /**
     * The caller's copy may still be pending while the stored one was accepted in the meantime: the state read
     * under the lock decides, and an accepted member is already a member.
     */
    @Test
    void aMembershipAcceptedBeforeTheLockIsRefusedAndNothingIsIssuedOrSent() {
        UUID userId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        User user = new User.Builder().id(userId).build();
        Community community = new Community.Builder().withId(communityId).build();
        CommunityMembership pending = new CommunityMembership.Builder()
                .withUser(user).withCommunity(community).withContactEmail("member@example.org").build();
        CommunityMembership accepted = CommunityMembershipMother.active()
                .withUser(user).withCommunity(community).withContactEmail("member@example.org").build();
        when(lockMembershipRepository.lock(UserId.of(userId), communityId)).thenReturn(true);
        when(getMembershipsRepository.findByUserIdAndCommunityId(userId, communityId))
                .thenReturn(Optional.of(accepted));

        assertThrows(MembershipAlreadyExistsException.class, () -> service().send(pending));

        verifyNoInteractions(createOneTimeTokenService, updateMembershipInvitationRepository, emailSender);
    }
}
