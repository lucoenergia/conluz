package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
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
    private GetMembershipsRepository getMembershipsRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;

    private GetMembershipsService service() {
        return new GetMembershipsServiceImpl(getMembershipsRepository, getCommunityRepository);
    }

    @Test
    void findByCommunityId_validatesCommunityExistsAndDelegatesToRepository() {
        Community community = CommunityMother.random().build();
        CommunityMembership membership = mock(CommunityMembership.class);
        List<CommunityMembership> expected = List.of(membership);

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(getMembershipsRepository.findByCommunityId(community.getId())).thenReturn(expected);

        List<CommunityMembership> result = service().findByCommunityId(community.getId());

        assertEquals(expected, result);
        verify(getMembershipsRepository).findByCommunityId(community.getId());
    }

    @Test
    void findByCommunityId_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getCommunityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().findByCommunityId(communityId));

        verifyNoInteractions(getMembershipsRepository);
    }
}
