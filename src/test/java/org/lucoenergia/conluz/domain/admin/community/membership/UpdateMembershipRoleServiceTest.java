package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.UpdateMembershipRoleServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateMembershipRoleServiceTest {

    @Mock
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private UserRepository userRepository;

    private UpdateMembershipRoleService service() {
        return new UpdateMembershipRoleServiceImpl(membershipJpaRepository, getCommunityRepository, userRepository);
    }

    @Test
    void updateRole_updatesRoleAndReturnsMembership() {
        Community community = CommunityMother.random().build();
        UserEntity userEntity = UserMother.randomUserEntity();
        UUID userId = userEntity.getId();

        CommunityEntity communityEntity = new CommunityEntity.Builder().withId(community.getId()).build();
        CommunityMembershipEntity entity = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(communityEntity)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
        when(membershipJpaRepository.findByUserId(userId)).thenReturn(List.of(entity));
        when(membershipJpaRepository.save(entity)).thenReturn(entity);

        CommunityMembership result = service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN);

        assertNotNull(result);
        assertEquals(CommunityRole.COMMUNITY_ADMIN, entity.getRole());
        verify(membershipJpaRepository).save(entity);
    }

    @Test
    void updateRole_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getCommunityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class,
                () -> service().updateRole(communityId, UUID.randomUUID(), CommunityRole.COMMUNITY_ADMIN));
    }

    @Test
    void updateRole_throwsUserNotFoundException_whenUserNotFound() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service().updateRole(community.getId(), userId, CommunityRole.COMMUNITY_ADMIN));
    }
}
