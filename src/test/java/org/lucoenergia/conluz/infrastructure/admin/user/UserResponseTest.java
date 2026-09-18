package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;

class UserResponseTest {

    @Test
    void testMapsIsPlatformAdminAndMemberships() {
        Community community1 = CommunityMother.random().build();
        Community community2 = CommunityMother.random().build();

        User user = UserMother.randomUser();
        user.setPlatformAdmin(true);

        CommunityMembership membership1 = new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community1)
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .withEnabled(true)
                .build();

        CommunityMembership membership2 = new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(community2)
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(true)
                .build();

        user.setMemberships(List.of(membership1, membership2));

        UserResponse response = new UserResponse(user, anyCapabilities());

        assertTrue(response.getIsPlatformAdmin());
        Map<String, String> memberships = response.getMemberships();
        assertEquals(2, memberships.size());
        assertEquals(CommunityRole.COMMUNITY_ADMIN.name(), memberships.get(community1.getId().toString()));
        assertEquals(CommunityRole.COMMUNITY_MEMBER.name(), memberships.get(community2.getId().toString()));
    }

    @Test
    void testMapsIsPlatformAdminFalseWhenNotSet() {
        User user = UserMother.randomUser();

        UserResponse response = new UserResponse(user, anyCapabilities());

        assertFalse(response.getIsPlatformAdmin());
        assertTrue(response.getMemberships().isEmpty());
    }

    /**
     * These tests are about the mapping of the user's own fields, not about access. The capabilities
     * are a precomputed argument here, exactly as they are in production -- the response never
     * decides them.
     */
    private UserCapabilitiesResponse anyCapabilities() {
        return UserCapabilitiesResponse.builder().build();
    }
}
