package org.lucoenergia.conluz.domain.admin.community.access.policy;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Platform-wide rules never answer {@link AccessDecision#NOT_VISIBLE}: a role check says nothing
 * about whether any object exists, so adding a visibility gate would turn today's 403 into a 404.
 */
class PlatformAccessPolicyTest {

    private final PlatformAccessPolicy policy = new PlatformAccessPolicy();

    // --- canAdministerPlatform ---

    @Test
    void canAdministerPlatform_allows_aPlatformAdmin() {
        assertEquals(AccessDecision.ALLOWED, policy.canAdministerPlatform(PolicyFixtures.platformAdmin()));
    }

    @Test
    void canAdministerPlatform_forbids_aCommunityAdmin() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canAdministerPlatform(PolicyFixtures.adminOf(PolicyFixtures.community())));
    }

    @Test
    void canAdministerPlatform_forbids_aPlainMember() {
        assertEquals(AccessDecision.FORBIDDEN,
                policy.canAdministerPlatform(PolicyFixtures.memberOf(PolicyFixtures.community())));
    }

    @Test
    void canAdministerPlatform_forbids_whenTheCallerIsNull() {
        assertEquals(AccessDecision.FORBIDDEN, policy.canAdministerPlatform(null));
    }

    // --- canAdministerOtherUser ---

    @Test
    void canAdministerOtherUser_allows_aPlatformAdminActingOnSomeoneElse() {
        assertEquals(AccessDecision.ALLOWED,
                policy.canAdministerOtherUser(PolicyFixtures.platformAdmin(), UUID.randomUUID()));
    }

    @Test
    void canAdministerOtherUser_forbids_aPlatformAdminActingOnThemselves() {
        User admin = PolicyFixtures.platformAdmin();
        assertEquals(AccessDecision.FORBIDDEN, policy.canAdministerOtherUser(admin, admin.getId()));
    }

    @Test
    void canAdministerOtherUser_forbids_aCommunityAdmin() {
        assertEquals(AccessDecision.FORBIDDEN, policy.canAdministerOtherUser(
                PolicyFixtures.adminOf(PolicyFixtures.community()), UUID.randomUUID()));
    }

    @Test
    void canAdministerOtherUser_allows_aPlatformAdminWhenNoUserIsNamed() {
        // isCurrentUser is false for a null id, matching "!isCurrentUser(#userId)" in the SpEL it replaces.
        assertEquals(AccessDecision.ALLOWED,
                policy.canAdministerOtherUser(PolicyFixtures.platformAdmin(), null));
    }

    @Test
    void canAdministerOtherUser_forbids_whenTheCallerIsNull() {
        assertEquals(AccessDecision.FORBIDDEN, policy.canAdministerOtherUser(null, UUID.randomUUID()));
    }
}
