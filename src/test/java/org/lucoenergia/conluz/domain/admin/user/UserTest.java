package org.lucoenergia.conluz.domain.admin.user;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void partnerRoleGrantsOnlyPartnerAuthority() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        assertEquals(1, authorities.size());
        assertTrue(containsAuthority(authorities, "ROLE_PARTNER"));
    }

    @Test
    void adminRoleGrantsOnlyAdminAuthority() {
        User user = UserMother.randomUser();
        user.setRole(Role.ADMIN);

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        assertEquals(1, authorities.size());
        assertTrue(containsAuthority(authorities, "ROLE_ADMIN"));
    }

    @Test
    void platformAdminFlagAddsPlatformAdminAuthority() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        user.setPlatformAdmin(true);

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        assertEquals(2, authorities.size());
        assertTrue(containsAuthority(authorities, "ROLE_PARTNER"));
        assertTrue(containsAuthority(authorities, "ROLE_PLATFORM_ADMIN"));
    }

    @Test
    void activeCommunityAdminMembershipGrantsCommunityAdminAuthority() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        user.setMemberships(List.of(membershipWith(user, CommunityRole.COMMUNITY_ADMIN, true)));

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        assertEquals(2, authorities.size());
        assertTrue(containsAuthority(authorities, "ROLE_PARTNER"));
        assertTrue(containsAuthority(authorities, "ROLE_COMMUNITY_ADMIN"));
    }

    @Test
    void disabledCommunityAdminMembershipDoesNotGrantCommunityAdminAuthority() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        user.setMemberships(List.of(membershipWith(user, CommunityRole.COMMUNITY_ADMIN, false)));

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        assertEquals(1, authorities.size());
        assertTrue(containsAuthority(authorities, "ROLE_PARTNER"));
        assertFalse(containsAuthority(authorities, "ROLE_COMMUNITY_ADMIN"));
    }

    @Test
    void communityMemberMembershipDoesNotGrantCommunityAdminAuthority() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        user.setMemberships(List.of(membershipWith(user, CommunityRole.COMMUNITY_MEMBER, true)));

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        assertEquals(1, authorities.size());
        assertTrue(containsAuthority(authorities, "ROLE_PARTNER"));
        assertFalse(containsAuthority(authorities, "ROLE_COMMUNITY_ADMIN"));
    }

    @Test
    void nullMembershipsDoNotGrantCommunityAdminAuthority() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        user.setMemberships(null);

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        assertEquals(1, authorities.size());
        assertTrue(containsAuthority(authorities, "ROLE_PARTNER"));
        assertFalse(containsAuthority(authorities, "ROLE_COMMUNITY_ADMIN"));
    }

    @Test
    void allFlagsSetGrantsAllAuthorities() {
        User user = UserMother.randomUser();
        user.setRole(Role.PARTNER);
        user.setPlatformAdmin(true);
        user.setMemberships(List.of(membershipWith(user, CommunityRole.COMMUNITY_ADMIN, true)));

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        assertEquals(3, authorities.size());
        assertTrue(containsAuthority(authorities, "ROLE_PARTNER"));
        assertTrue(containsAuthority(authorities, "ROLE_PLATFORM_ADMIN"));
        assertTrue(containsAuthority(authorities, "ROLE_COMMUNITY_ADMIN"));
    }

    private CommunityMembership membershipWith(User user, CommunityRole communityRole, boolean enabled) {
        Community community = CommunityMother.random().build();
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community)
                .withRole(communityRole)
                .withEnabled(enabled)
                .build();
    }

    private boolean containsAuthority(Collection<? extends GrantedAuthority> authorities, String authority) {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals(authority));
    }
}
