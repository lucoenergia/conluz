package org.lucoenergia.conluz.domain.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;

import java.time.Instant;
import java.util.List;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class LastActiveCommunityAdminRuleTest {

    private static final LongSupplier UNREAD = () -> {
        throw new AssertionError("the count must not be read for a membership that does not count");
    };

    @Test
    @DisplayName("MEM-033 the only active admin is the last one")
    void theOnlyActiveAdmin_isBlocked() {
        assertThat(LastActiveCommunityAdminRule.blocksRemoval(membership(CommunityRole.COMMUNITY_ADMIN, true, true,
                true), () -> 1)).isTrue();
    }

    @Test
    @DisplayName("MEM-033 an active admin is not the last one while another active admin remains")
    void anActiveAdminWithAnother_isNotBlocked() {
        assertThat(LastActiveCommunityAdminRule.blocksRemoval(membership(CommunityRole.COMMUNITY_ADMIN, true, true,
                true), () -> 2)).isFalse();
    }

    @Test
    @DisplayName("MEM-033 a pending admin is never the last one, and the count is not read")
    void aPendingAdmin_isNotBlocked() {
        assertThat(LastActiveCommunityAdminRule.blocksRemoval(membership(CommunityRole.COMMUNITY_ADMIN, false, true,
                true), UNREAD)).isFalse();
    }

    @Test
    @DisplayName("MEM-033 a suspended admin is never the last one, and the count is not read")
    void aSuspendedAdmin_isNotBlocked() {
        assertThat(LastActiveCommunityAdminRule.blocksRemoval(membership(CommunityRole.COMMUNITY_ADMIN, true, false,
                true), UNREAD)).isFalse();
    }

    @Test
    @DisplayName("MEM-033 an admin whose account is disabled is never the last one, and the count is not read")
    void anAdminWithADisabledAccount_isNotBlocked() {
        assertThat(LastActiveCommunityAdminRule.blocksRemoval(membership(CommunityRole.COMMUNITY_ADMIN, true, true,
                false), UNREAD)).isFalse();
    }

    @Test
    @DisplayName("MEM-033 a member is never the last admin, and the count is not read")
    void aMember_isNotBlocked() {
        assertThat(LastActiveCommunityAdminRule.blocksRemoval(membership(CommunityRole.COMMUNITY_MEMBER, true, true,
                true), UNREAD)).isFalse();
    }

    @Test
    @DisplayName("MEM-033 only active admins with an enabled account are counted from a roster")
    void activeAdmins_countsOnlyTheAdminsWhoCanAct() {
        List<CommunityMembership> roster = List.of(
                membership(CommunityRole.COMMUNITY_ADMIN, true, true, true),
                membership(CommunityRole.COMMUNITY_ADMIN, true, true, true),
                membership(CommunityRole.COMMUNITY_ADMIN, false, true, true),
                membership(CommunityRole.COMMUNITY_ADMIN, true, false, true),
                membership(CommunityRole.COMMUNITY_ADMIN, true, true, false),
                membership(CommunityRole.COMMUNITY_MEMBER, true, true, true));

        assertThat(LastActiveCommunityAdminRule.activeAdmins(roster)).isEqualTo(2);
    }

    private static CommunityMembership membership(CommunityRole role, boolean accepted, boolean enabled,
                                                  boolean accountEnabled) {
        User user = UserMother.randomUser();
        user.setEnabled(accountEnabled);
        return new CommunityMembership.Builder()
                .withUser(user)
                .withRole(role)
                .withAcceptedAt(accepted ? Instant.parse("2026-01-01T00:00:00Z") : null)
                .withEnabled(enabled)
                .build();
    }
}
