package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.DeleteMembershipServiceImpl;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteMembershipServiceTest {

    @Mock
    private CommunityMembershipJpaRepository membershipJpaRepository;

    private DeleteMembershipService service() {
        return new DeleteMembershipServiceImpl(membershipJpaRepository);
    }

    @Test
    void delete_removesMatchingMemberships() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommunityEntity communityEntity = new CommunityEntity.Builder().withId(communityId).build();
        CommunityMembershipEntity matching = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(communityEntity)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();

        CommunityEntity otherCommunity = new CommunityEntity.Builder().withId(UUID.randomUUID()).build();
        CommunityMembershipEntity nonMatching = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(otherCommunity)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();

        when(membershipJpaRepository.findByUserId(userId)).thenReturn(List.of(matching, nonMatching));

        service().delete(communityId, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommunityMembershipEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(membershipJpaRepository).deleteAll(captor.capture());
        List<CommunityMembershipEntity> deleted = captor.getValue();
        assertTrue(deleted.contains(matching));
        assertTrue(deleted.stream().noneMatch(e -> e.equals(nonMatching)));
    }

    @Test
    void delete_deletesEmptyList_whenNoMembershipMatchesCommunity() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommunityEntity otherCommunity = new CommunityEntity.Builder().withId(UUID.randomUUID()).build();
        CommunityMembershipEntity nonMatching = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(otherCommunity)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();

        when(membershipJpaRepository.findByUserId(userId)).thenReturn(List.of(nonMatching));

        service().delete(communityId, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommunityMembershipEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(membershipJpaRepository).deleteAll(captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }
}
