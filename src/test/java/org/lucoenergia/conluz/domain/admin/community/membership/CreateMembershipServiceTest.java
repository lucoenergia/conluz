package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.CreateMembershipServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateMembershipServiceTest {

    @Mock
    private CreateMembershipRepository createMembershipRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;

    private CreateMembershipService service() {
        return new CreateMembershipServiceImpl(createMembershipRepository, getCommunityRepository);
    }

    @Test
    void create_validatesCommunityExistsAndDelegatesToRepository() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        CommunityMembership expected = mock(CommunityMembership.class);

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(createMembershipRepository.create(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER))
                .thenReturn(expected);

        CommunityMembership result = service().create(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER);

        assertSame(expected, result);
        verify(createMembershipRepository).create(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER);
    }

    @Test
    void create_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getCommunityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class,
                () -> service().create(communityId, UUID.randomUUID(), CommunityRole.COMMUNITY_MEMBER));

        verifyNoInteractions(createMembershipRepository);
    }
}
