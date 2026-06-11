package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.MembershipAccessGuard;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
        when(helper.getCurrentUser()).thenReturn(Optional.of(admin));

        assertTrue(guard().canManageMemberships(UUID.randomUUID()));
    }

    @Test
    void canManageMemberships_returnsTrue_whenUserIsCommunityAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasCommunityAdminRoleIn(user, community.getId())).thenReturn(true);

        assertTrue(guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_returnsFalse_whenUserIsCommunityMember() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        when(helper.getCurrentUser()).thenReturn(Optional.of(user));
        when(helper.hasCommunityAdminRoleIn(user, community.getId())).thenReturn(false);

        assertFalse(guard().canManageMemberships(community.getId()));
    }

    @Test
    void canManageMemberships_returnsFalse_whenNoAuthenticatedUser() {
        when(helper.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(guard().canManageMemberships(UUID.randomUUID()));
    }
}
