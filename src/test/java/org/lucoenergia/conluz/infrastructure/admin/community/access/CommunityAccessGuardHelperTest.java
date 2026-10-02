package org.lucoenergia.conluz.infrastructure.admin.community.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * What is left of the helper once the membership predicates moved to
 * {@code CallerMemberships}: resolving the caller, and the one scope question whose answer lives in
 * the database. The predicates' assertions live on in {@code CallerMembershipsTest}.
 */
@ExtendWith(MockitoExtension.class)
class CommunityAccessGuardHelperTest {

    @Mock
    private AuthService authService;
    @Mock
    private GetCommunityRepository getCommunityRepository;

    private CommunityAccessGuardHelper helper() {
        return new CommunityAccessGuardHelper(authService, getCommunityRepository);
    }

    // --- getCurrentUser ---

    @Test
    void getCurrentUser_passesThroughTheAuthenticatedUser() {
        User user = UserMother.randomUser();
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertEquals(Optional.of(user), helper().getCurrentUser());
    }

    @Test
    void getCurrentUser_isEmpty_whenNobodyIsAuthenticated() {
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertTrue(helper().getCurrentUser().isEmpty());
    }

    // --- visibleCommunityIds ---

    @Test
    void visibleCommunityIds_returnsEmpty_whenUserIsNull() {
        assertTrue(helper().visibleCommunityIds(null).isEmpty());
    }

    @Test
    void visibleCommunityIds_returnsAllIds_whenUserIsPlatformAdmin() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);
        when(getCommunityRepository.findAllIds()).thenReturn(Set.of(community.getId()));

        Set<UUID> result = helper().visibleCommunityIds(user);
        assertTrue(result.contains(community.getId()));
    }

    @Test
    void visibleCommunityIds_returnsEmpty_whenMembershipsIsNull() {
        User user = UserMother.randomUser();
        user.setMemberships(null);
        assertTrue(helper().visibleCommunityIds(user).isEmpty());
    }

    @Test
    void visibleCommunityIds_returnsEnabledCommunityIds() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_MEMBER).withEnabled(true).build();
        user.setMemberships(List.of(membership));

        Set<UUID> result = helper().visibleCommunityIds(user);
        assertTrue(result.contains(community.getId()));
    }

    @Test
    void visibleCommunityIds_excludesDisabledMemberships() {
        Community community = CommunityMother.random().build();
        User user = UserMother.randomUser();
        CommunityMembership membership = new CommunityMembership.Builder()
                .withId(UUID.randomUUID()).withUser(user).withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN).withEnabled(false).build();
        user.setMemberships(List.of(membership));

        assertTrue(helper().visibleCommunityIds(user).isEmpty());
    }
}
