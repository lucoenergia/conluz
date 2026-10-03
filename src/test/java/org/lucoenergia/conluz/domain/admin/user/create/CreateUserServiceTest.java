package org.lucoenergia.conluz.domain.admin.user.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.create.CreateUserServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void create_withCommunity_flagsTheUserAsHavingToChangeThePasswordChosenByTheCreator() {
        User user = UserMother.randomUser();
        when(repository.create(user)).thenReturn(user);

        service().create(user, UUID.randomUUID(), CommunityRole.COMMUNITY_MEMBER);

        verify(repository).create(argThat(User::mustChangePassword));
    }

    @Test
    void create_withoutCommunity_flagsTheUserAsHavingToChangeThePasswordChosenByTheCreator() {
        User user = UserMother.randomUser();
        when(repository.create(user)).thenReturn(user);

        service().create(user, null, null);

        verify(repository).create(argThat(User::mustChangePassword));
    }

    @Test
    void createFromImport_flagsTheUserAsHavingToChangeThePasswordChosenByTheImporter() {
        User user = UserMother.randomUser();
        UUID importCommunityId = UUID.randomUUID();
        when(repository.create(user)).thenReturn(user);

        service().createFromImport(user, null, importCommunityId, CommunityRole.COMMUNITY_MEMBER);

        verify(repository).create(argThat(User::mustChangePassword));
    }

    @Test
    void create_withTheUserOnly_doesNotFlagTheUserAsHavingToChangeThePassword() {
        User user = UserMother.randomUser();
        when(repository.create(user)).thenReturn(user);

        service().create(user);

        verify(repository).create(argThat(created -> !created.mustChangePassword()));
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

    @Test
    void createFromImport_withRowCommunityEqualToImportCommunity_createsMembershipInImportCommunity() {
        User user = UserMother.randomUser();
        UUID importCommunityId = UUID.randomUUID();
        when(repository.create(user)).thenReturn(user);

        service().createFromImport(user, importCommunityId.toString(), importCommunityId,
                CommunityRole.COMMUNITY_ADMIN);

        verify(createMembershipService).create(importCommunityId, user.getId(), CommunityRole.COMMUNITY_ADMIN);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void createFromImport_withBlankRowCommunity_createsMembershipInImportCommunity(String rowCommunityId) {
        User user = UserMother.randomUser();
        UUID importCommunityId = UUID.randomUUID();
        when(repository.create(user)).thenReturn(user);

        service().createFromImport(user, rowCommunityId, importCommunityId, null);

        verify(createMembershipService).create(importCommunityId, user.getId(), CommunityRole.COMMUNITY_MEMBER);
    }

    @Test
    void createFromImport_withRowCommunityDifferentFromImportCommunity_createsNothing() {
        User user = UserMother.randomUser();

        assertThrows(ImportRowCommunityMismatchException.class, () -> service().createFromImport(user,
                UUID.randomUUID().toString(), UUID.randomUUID(), CommunityRole.COMMUNITY_MEMBER));

        verifyNoInteractions(repository, createMembershipService);
    }

    @Test
    void createFromImport_withMalformedRowCommunity_createsNothing() {
        User user = UserMother.randomUser();

        assertThrows(ImportRowCommunityMismatchException.class, () -> service().createFromImport(user,
                "not-a-uuid", UUID.randomUUID(), CommunityRole.COMMUNITY_MEMBER));

        verifyNoInteractions(repository, createMembershipService);
    }

    @Test
    void createFromImport_withRowCommunityButNoImportCommunity_createsNothing() {
        User user = UserMother.randomUser();

        assertThrows(ImportRowCommunityMismatchException.class, () -> service().createFromImport(user,
                UUID.randomUUID().toString(), null, CommunityRole.COMMUNITY_MEMBER));

        verifyNoInteractions(repository, createMembershipService);
    }
}
