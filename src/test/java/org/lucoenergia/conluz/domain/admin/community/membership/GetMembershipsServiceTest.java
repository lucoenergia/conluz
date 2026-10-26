package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.GetMembershipsServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetMembershipsServiceTest {

    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private GetOneTimeTokenService getOneTimeTokenService;

    private GetMembershipsService service() {
        return new GetMembershipsServiceImpl(getMembershipsRepository, getOneTimeTokenService);
    }

    @Test
    void findByCommunityId_delegatesToRepository() {
        Community community = CommunityMother.random().build();
        CommunityMembership invited = new CommunityMembership.Builder().withId(UUID.randomUUID()).build();
        CommunityMembership notInvited = new CommunityMembership.Builder().withId(UUID.randomUUID()).build();
        Instant expiry = Instant.parse("2026-01-08T00:00:00Z");

        when(getMembershipsRepository.findByCommunityId(community.getId())).thenReturn(List.of(invited, notInvited));
        when(getOneTimeTokenService.findValidExpiriesInCommunity(community.getId(), OneTimeTokenPurpose.INVITATION))
                .thenReturn(Map.of(invited.getId(), expiry));

        List<MembershipInRoster> result = service().findByCommunityId(community.getId());

        assertEquals(List.of(new MembershipInRoster(invited, expiry, false),
                new MembershipInRoster(notInvited, null, false)), result);
        verify(getMembershipsRepository).findByCommunityId(community.getId());
        verify(getOneTimeTokenService).findValidExpiriesInCommunity(community.getId(), OneTimeTokenPurpose.INVITATION);
    }

    @Test
    @DisplayName("MEM-033 each membership of the roster says whether it is the last active admin, counted from the "
            + "roster without another query")
    void findByCommunityId_marksTheLastActiveAdmin() {
        Community community = CommunityMother.random().build();
        CommunityMembership active = admin(Instant.parse("2026-01-01T00:00:00Z"), true);
        CommunityMembership pending = admin(null, true);
        CommunityMembership suspended = admin(Instant.parse("2026-01-01T00:00:00Z"), false);
        when(getMembershipsRepository.findByCommunityId(community.getId()))
                .thenReturn(List.of(active, pending, suspended));
        when(getOneTimeTokenService.findValidExpiriesInCommunity(community.getId(), OneTimeTokenPurpose.INVITATION))
                .thenReturn(Map.of());

        List<MembershipInRoster> result = service().findByCommunityId(community.getId());

        assertEquals(List.of(true, false, false), result.stream().map(MembershipInRoster::lastActiveAdmin).toList());
        verify(getMembershipsRepository, never()).countActiveAdmins(any());
    }

    private static CommunityMembership admin(Instant acceptedAt, boolean enabled) {
        User user = UserMother.randomUser();
        user.setEnabled(true);
        return new CommunityMembership.Builder().withId(UUID.randomUUID()).withUser(user)
                .withRole(CommunityRole.COMMUNITY_ADMIN).withAcceptedAt(acceptedAt).withEnabled(enabled).build();
    }

    @Test
    void findByCommunityId_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getMembershipsRepository.findByCommunityId(communityId))
                .thenThrow(new CommunityNotFoundException(communityId));

        assertThrows(CommunityNotFoundException.class, () -> service().findByCommunityId(communityId));

        verifyNoInteractions(getOneTimeTokenService);
    }
}
