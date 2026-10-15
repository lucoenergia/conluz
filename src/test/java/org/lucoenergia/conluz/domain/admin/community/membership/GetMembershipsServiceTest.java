package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.GetMembershipsServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetMembershipsServiceTest {

    @Mock
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private GetOneTimeTokenService getOneTimeTokenService;

    private GetMembershipsService service() {
        return new GetMembershipsServiceImpl(getMembershipsRepository, getCommunityRepository, getOneTimeTokenService);
    }

    @Test
    void findByCommunityId_validatesCommunityExistsAndDelegatesToRepository() {
        Community community = CommunityMother.random().build();
        CommunityMembership invited = new CommunityMembership.Builder().withId(UUID.randomUUID()).build();
        CommunityMembership notInvited = new CommunityMembership.Builder().withId(UUID.randomUUID()).build();
        Instant expiry = Instant.parse("2026-01-08T00:00:00Z");

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(getMembershipsRepository.findByCommunityId(community.getId())).thenReturn(List.of(invited, notInvited));
        when(getOneTimeTokenService.findValidExpiriesInCommunity(community.getId(), OneTimeTokenPurpose.INVITATION))
                .thenReturn(Map.of(invited.getId(), expiry));

        List<MembershipWithInvitation> result = service().findByCommunityId(community.getId());

        assertEquals(List.of(new MembershipWithInvitation(invited, expiry),
                new MembershipWithInvitation(notInvited, null)), result);
        verify(getMembershipsRepository).findByCommunityId(community.getId());
        verify(getOneTimeTokenService).findValidExpiriesInCommunity(community.getId(), OneTimeTokenPurpose.INVITATION);
    }

    @Test
    void findByCommunityId_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getCommunityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().findByCommunityId(communityId));

        verifyNoInteractions(getMembershipsRepository, getOneTimeTokenService);
    }
}
