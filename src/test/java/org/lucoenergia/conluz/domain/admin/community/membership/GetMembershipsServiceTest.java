package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.GetMembershipsServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetMembershipsServiceTest {

    @Mock
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;

    private GetMembershipsService service() {
        return new GetMembershipsServiceImpl(membershipJpaRepository, getCommunityRepository);
    }

    @Test
    void findByCommunityId_returnsAllMembershipsForCommunity() {
        Community community = CommunityMother.random().build();
        CommunityEntity communityEntity = new CommunityEntity.Builder().withId(community.getId()).build();

        CommunityMembershipEntity entity = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(communityEntity)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(membershipJpaRepository.findByCommunityId(community.getId())).thenReturn(List.of(entity));

        List<CommunityMembership> result = service().findByCommunityId(community.getId());

        assertEquals(1, result.size());
        assertEquals(CommunityRole.COMMUNITY_MEMBER, result.get(0).getRole());
    }

    @Test
    void findByCommunityId_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getCommunityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().findByCommunityId(communityId));

        verifyNoInteractions(membershipJpaRepository);
    }
}
