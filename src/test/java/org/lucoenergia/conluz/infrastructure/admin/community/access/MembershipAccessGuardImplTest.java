package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.access.MembershipAccessGuard;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipAccessGuardImplTest {

    @Mock
    private CommunityAccessGuardHelper helper;

    private MembershipAccessGuard guard() {
        return new MembershipAccessGuardImpl(helper);
    }

    @Test
    void canManageMemberships_returnsTrue_whenUserIsPlatformAdmin() {
        User admin = UserMother.randomUser();
        admin.setPlatformAdmin(true);
        UUID communityId = UUID.randomUUID();
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));
        when(helper.canSeeCommunity(admin, communityId)).thenReturn(true);

        assertTrue(guard().canManageMemberships(communityId));
    }

    @Test
    void canManageMemberships_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.canSeeCommunity(user, community.getId())).thenReturn(true);
        when(helper.hasCommunityAdminRoleIn(user, community.getId())).thenReturn(true);

        assertTrue(guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_returnsFalse_whenUserIsCommunityMember() {
        // A member can see the community but is not an admin -> 403 (return false), not 404.
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.canSeeCommunity(user, community.getId())).thenReturn(true);
        when(helper.hasCommunityAdminRoleIn(user, community.getId())).thenReturn(false);

        assertFalse(guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_throwsNotFound_whenUserIsNotMember() {
        // A non-member cannot see the community -> 404 to avoid leaking its existence.
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.canSeeCommunity(user, community.getId())).thenReturn(false);

        assertThrows(CommunityNotFoundException.class, () -> guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManageMemberships(UUID.randomUUID()));
    }
}
