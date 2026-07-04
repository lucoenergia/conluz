package org.lucoenergia.conluz.domain.admin.user.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.create.CreateUserServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserServiceTest {

    @Mock
    private CreateUserRepository repository;
    @Mock
    private CreateMembershipService createMembershipService;

    private CreateUserService service() {
        return new CreateUserServiceImpl(repository, createMembershipService);
    }

    @Test
    void create_withExplicitCommunityId_createsMembershipInThatCommunity() {
        User user = UserMother.randomUser();
        UUID communityId = UUID.randomUUID();
        when(repository.create(user)).thenReturn(user);

        service().create(user, communityId, CommunityRole.COMMUNITY_MEMBER);

        verify(createMembershipService).create(communityId, user.getId(), CommunityRole.COMMUNITY_MEMBER);
    }

    @Test
    void create_withNullCommunityId_createsAUserNotJoinedToAnyCommunity() {
        User user = UserMother.randomUser();

        when(repository.create(user)).thenReturn(user);

        service().create(user);

        verifyNoInteractions(createMembershipService);
    }
}
