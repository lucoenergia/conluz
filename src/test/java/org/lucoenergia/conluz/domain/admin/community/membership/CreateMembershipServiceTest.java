package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.CreateMembershipServiceImpl;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateMembershipServiceTest {

    @Mock
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private UserRepository userRepository;

    private CreateMembershipService service() {
        return new CreateMembershipServiceImpl(membershipJpaRepository, getCommunityRepository, userRepository);
    }

    @Test
    void create_savesEntityWithEnabledTrueAndReturnsResult() {
        Community community = CommunityMother.random().build();
        UserEntity userEntity = UserMother.randomUserEntity();
        UUID userId = userEntity.getId();

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));

        CommunityMembershipEntity saved = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();
        when(membershipJpaRepository.save(any())).thenReturn(saved);

        CommunityMembership result = service().create(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER);

        assertNotNull(result);
        ArgumentCaptor<CommunityMembershipEntity> captor = ArgumentCaptor.forClass(CommunityMembershipEntity.class);
        verify(membershipJpaRepository).save(captor.capture());
        assertTrue(captor.getValue().isEnabled());
        assertEquals(CommunityRole.COMMUNITY_MEMBER, captor.getValue().getRole());
    }

    @Test
    void create_throwsCommunityNotFoundException_whenCommunityNotFound() {
        UUID communityId = UUID.randomUUID();
        when(getCommunityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class,
                () -> service().create(communityId, UUID.randomUUID(), CommunityRole.COMMUNITY_MEMBER));

        verifyNoInteractions(membershipJpaRepository);
    }

    @Test
    void create_throwsUserNotFoundException_whenUserNotFound() {
        Community community = CommunityMother.random().build();
        UUID userId = UUID.randomUUID();

        when(getCommunityRepository.findById(community.getId())).thenReturn(Optional.of(community));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service().create(community.getId(), userId, CommunityRole.COMMUNITY_MEMBER));

        verifyNoInteractions(membershipJpaRepository);
    }
}
