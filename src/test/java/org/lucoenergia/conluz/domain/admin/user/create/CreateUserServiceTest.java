package org.lucoenergia.conluz.domain.admin.user.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.infrastructure.admin.user.create.CreateUserServiceImpl;
import org.lucoenergia.conluz.infrastructure.shared.security.community.CommunityContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserServiceTest {

    @Mock
    private CreateUserRepository repository;
    @Mock
    private AuthService authService;
    @Mock
    private CommunityContext communityContext;
    @Mock
    private CreateMembershipService createMembershipService;

    private CreateUserService service() {
        return new CreateUserServiceImpl(repository, authService, communityContext, createMembershipService);
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
    void create_withNullCommunityId_autoResolvesFromContextForRegularPartner() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(false);

        User user = UserMother.randomUser();
        UUID contextCommunityId = UUID.randomUUID();

        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));
        when(communityContext.getActiveCommunityId()).thenReturn(Optional.of(contextCommunityId));
        when(repository.create(user)).thenReturn(user);

        service().create(user);

        verify(createMembershipService).create(eq(contextCommunityId), eq(user.getId()), any());
    }

    @Test
    void create_withNullCommunityId_skipsAutoResolveForPlatformAdmin() {
        User caller = UserMother.randomUser();
        caller.setPlatformAdmin(true);

        User user = UserMother.randomUser();

        when(authService.getCurrentUser()).thenReturn(Optional.of(caller));
        when(repository.create(user)).thenReturn(user);

        service().create(user);

        verifyNoInteractions(createMembershipService);
    }
}
