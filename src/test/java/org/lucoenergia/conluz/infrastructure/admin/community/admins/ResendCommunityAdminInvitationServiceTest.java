package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.admins.CommunityAdmin;
import org.lucoenergia.conluz.domain.admin.community.admins.ResendCommunityAdminInvitationService;
import org.lucoenergia.conluz.domain.admin.community.admins.notification.CommunityAdminChangeNotifier;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationResending;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationSending;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendCommunityAdminInvitationServiceTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserId PLATFORM_ADMIN = UserId.of(UUID.randomUUID());

    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private InvitationResending resending;
    @Mock
    private InvitationSending sending;
    @Mock
    private CommunityAdminChangeNotifier notifier;

    private ResendCommunityAdminInvitationService service() {
        return new ResendCommunityAdminInvitationServiceImpl(getCommunityRepository, resending, sending,
                notifier);
    }

    @Test
    @DisplayName("MEM-022 the invitation is resent with the shared rules, to admin memberships only")
    void resendsToAdminMembershipsOnly() {
        CommunityMembership invited = new CommunityMembership.Builder()
                .withUser(new User.Builder().id(USER_ID).personalId("12345678A").build())
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .build();
        Instant expiry = Instant.parse("2026-01-08T00:00:00Z");
        when(getCommunityRepository.findById(COMMUNITY_ID))
                .thenReturn(Optional.of(new Community.Builder().withId(COMMUNITY_ID).build()));
        when(resending.resend(eq(COMMUNITY_ID), eq(UserId.of(USER_ID)), any())).thenReturn(invited);
        when(sending.withInvitation(invited)).thenReturn(new MembershipWithInvitation(invited, expiry));

        CommunityAdmin admin = service().resend(COMMUNITY_ID, UserId.of(USER_ID), PLATFORM_ADMIN);

        assertEquals("12345678A", admin.personalId());
        assertEquals(expiry, admin.invitationExpiresAt());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Predicate<CommunityMembership>> scope = ArgumentCaptor.forClass(Predicate.class);
        verify(resending).resend(eq(COMMUNITY_ID), eq(UserId.of(USER_ID)), scope.capture());
        assertTrue(scope.getValue().test(withRole(CommunityRole.COMMUNITY_ADMIN)));
        assertFalse(scope.getValue().test(withRole(CommunityRole.COMMUNITY_MEMBER)));
    }

    @Test
    void anUnknownCommunity_isNotFound_andNothingIsResent() {
        when(getCommunityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class,
                () -> service().resend(COMMUNITY_ID, UserId.of(USER_ID), PLATFORM_ADMIN));

        verifyNoInteractions(resending, sending);
    }

    private static CommunityMembership withRole(CommunityRole role) {
        return new CommunityMembership.Builder().withRole(role).build();
    }
}
