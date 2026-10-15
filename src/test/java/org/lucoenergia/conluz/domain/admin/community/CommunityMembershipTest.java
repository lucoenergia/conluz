package org.lucoenergia.conluz.domain.admin.community;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityMembershipTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("MEM-004 an accepted, enabled membership is active")
    void isActive_whenAcceptedAndEnabled() {
        assertTrue(membership(ACCEPTED_AT, true).isActive());
    }

    @Test
    @DisplayName("MEM-004 a pending membership is not active, even when enabled")
    void isNotActive_whenPendingAndEnabled() {
        assertFalse(membership(null, true).isActive());
    }

    @Test
    @DisplayName("MEM-004 a disabled membership is not active, even when accepted")
    void isNotActive_whenAcceptedAndDisabled() {
        assertFalse(membership(ACCEPTED_AT, false).isActive());
    }

    @Test
    @DisplayName("MEM-004 a pending, disabled membership is not active")
    void isNotActive_whenPendingAndDisabled() {
        assertFalse(membership(null, false).isActive());
    }

    @Test
    @DisplayName("MEM-005 pending and disabled are kept apart: each state is readable on its own")
    void pendingAndDisabledAreIndependent() {
        CommunityMembership pendingButEnabled = membership(null, true);
        CommunityMembership acceptedButDisabled = membership(ACCEPTED_AT, false);

        assertTrue(pendingButEnabled.isEnabled());
        assertFalse(acceptedButDisabled.isEnabled());
        assertTrue(acceptedButDisabled.getAcceptedAt() != null);
        assertTrue(pendingButEnabled.getAcceptedAt() == null);
    }

    @Test
    @DisplayName("MEM-012 only a pending, enabled membership is awaiting acceptance")
    void isAwaitingAcceptance_onlyWhenPendingAndEnabled() {
        assertTrue(membership(null, true).isAwaitingAcceptance());
        assertFalse(membership(null, false).isAwaitingAcceptance());
        assertFalse(membership(ACCEPTED_AT, true).isAwaitingAcceptance());
        assertFalse(membership(ACCEPTED_AT, false).isAwaitingAcceptance());
    }

    private static CommunityMembership membership(Instant acceptedAt, boolean enabled) {
        return new CommunityMembership.Builder()
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withAcceptedAt(acceptedAt)
                .withEnabled(enabled)
                .build();
    }
}
