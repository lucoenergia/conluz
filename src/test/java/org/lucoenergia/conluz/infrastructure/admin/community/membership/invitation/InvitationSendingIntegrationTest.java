package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The shared invitation steps take their locks only inside the caller's transaction, which holds them until it ends.
 */
class InvitationSendingIntegrationTest extends BaseInvitationTest {

    // No interface: the steps are an infrastructure component shared by the invitation services
    @Autowired
    private InvitationSending sending;

    @Test
    void theAvailabilityCheck_needsNoTransaction() {
        assertDoesNotThrow(() -> sending.requireAvailable());
    }

    @Test
    void lockingOutsideATransaction_isRefused_andNothingIsIssued() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        UUID userId = registered(loginAsAdminOf(community), community.getId(), body);

        assertThrows(IllegalTransactionStateException.class,
                () -> sending.lockAndFind(community.getId(), UserId.of(userId)));
        assertThrows(IllegalTransactionStateException.class,
                () -> sending.lockAndRead(community.getId(), UserId.of(userId)));

        assertThat(tokenCount((String) body.get("personalId"))).isZero();
        assertThat(invitedAt((String) body.get("personalId"), community.getId())).isNull();
    }
}
