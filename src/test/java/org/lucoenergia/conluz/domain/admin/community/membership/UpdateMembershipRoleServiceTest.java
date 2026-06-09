package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.UpdateMembershipRoleServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateMembershipRoleServiceTest {

    @Mock
    private UpdateMembershipRoleRepository updateMembershipRoleRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;

    private UpdateMembershipRoleService service() {
        return new UpdateMembershipRoleServiceImpl(updateMembershipRoleRepository, getCommunityRepository);
    }

    @Test
    void updateRole_validatesCommunityExistsAndDelegatesToRepository() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();
        CommunityMembership expected = mock(CommunityMembership.class);

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(updateMembershipRoleRepository.updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN))
                .thenReturn(expected);

        CommunityMembership result = service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN);

        assertSame(expected, result);
        verify(updateMembershipRoleRepository).updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN);
    }

    @Test
    void updateRole_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getCommunityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class,
                () -> service().updateRole(communityId, UUID.randomUUID(), CommunityRole.COMMUNITY_ADMIN));

        verifyNoInteractions(updateMembershipRoleRepository);
    }
}
