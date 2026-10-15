package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException;
import org.lucoenergia.conluz.domain.admin.community.admins.CommunityAdmin;
import org.lucoenergia.conluz.domain.admin.community.admins.InviteCommunityAdminService;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisteredMember;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationSending;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.MembershipRegistration;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteCommunityAdminServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserId PLATFORM_ADMIN = UserId.of(UUID.randomUUID());
    private static final UserPersonalId PERSONAL_ID = UserPersonalId.of("12345678A");
    private static final MemberRecord RECORD = new MemberRecord("Marta Ruiz", null, "marta@example.org", null, null);

    @Mock
    private MembershipRegistration registration;
    @Mock
    private InvitationSending sending;

    private InviteCommunityAdminService service() {
        return new InviteCommunityAdminServiceImpl(registration, sending);
    }

    @Test
    @DisplayName("MEM-020 when invitations cannot be sent, nothing is registered")
    void whenSendingIsUnavailable_nothingIsRegistered() {
        doThrow(new MembershipInvitationUnavailableException()).when(sending).requireAvailable();

        assertThrows(MembershipInvitationUnavailableException.class,
                () -> service().invite(COMMUNITY_ID, PLATFORM_ADMIN, PERSONAL_ID, RECORD));

        verifyNoInteractions(registration);
        verify(sending, never()).issueAndSend(any());
    }

    @Test
    @DisplayName("MEM-020 the person is registered with the admin role, by the platform admin, then sent the "
            + "invitation")
    void thePersonIsRegisteredAsAdmin_thenInvited() {
        CommunityMembership pending = membership(null);
        CommunityMembership invited = membership(Instant.parse("2026-01-01T00:00:00Z"));
        Instant expiry = Instant.parse("2026-01-08T00:00:00Z");
        when(registration.register(COMMUNITY_ID, PLATFORM_ADMIN, PERSONAL_ID, RECORD, CommunityRole.COMMUNITY_ADMIN))
                .thenReturn(new RegisteredMember("12345678A", pending));
        when(sending.lockAndRead(COMMUNITY_ID, UserId.of(USER_ID))).thenReturn(pending);
        when(sending.issueAndSend(pending)).thenReturn(invited);
        when(sending.withInvitation(invited)).thenReturn(new MembershipWithInvitation(invited, expiry));

        CommunityAdmin admin = service().invite(COMMUNITY_ID, PLATFORM_ADMIN, PERSONAL_ID, RECORD);

        assertEquals("12345678A", admin.personalId());
        assertSame(invited, admin.membership());
        assertEquals(expiry, admin.invitationExpiresAt());
        InOrder order = inOrder(sending, registration);
        order.verify(sending).requireAvailable();
        order.verify(registration).register(COMMUNITY_ID, PLATFORM_ADMIN, PERSONAL_ID, RECORD,
                CommunityRole.COMMUNITY_ADMIN);
        order.verify(sending).lockAndRead(COMMUNITY_ID, UserId.of(USER_ID));
        order.verify(sending).issueAndSend(pending);
    }

    @Test
    @DisplayName("MEM-020 a person already in the community is refused, and nothing is sent")
    void anExistingMembership_isRefused_andNothingIsSent() {
        when(registration.register(COMMUNITY_ID, PLATFORM_ADMIN, PERSONAL_ID, RECORD, CommunityRole.COMMUNITY_ADMIN))
                .thenThrow(new MembershipAlreadyExistsException(COMMUNITY_ID, USER_ID));

        assertThrows(MembershipAlreadyExistsException.class,
                () -> service().invite(COMMUNITY_ID, PLATFORM_ADMIN, PERSONAL_ID, RECORD));

        verify(sending, never()).lockAndRead(any(), any());
        verify(sending, never()).issueAndSend(any());
    }

    private static CommunityMembership membership(Instant invitedAt) {
        return new CommunityMembership.Builder()
                .withUser(new User.Builder().id(USER_ID).build())
                .withCommunity(new Community.Builder().withId(COMMUNITY_ID).build())
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .withInvitedAt(invitedAt)
                .build();
    }
}
