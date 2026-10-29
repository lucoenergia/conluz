package org.lucoenergia.conluz.domain.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationResultTest {

    private static final UserId USER_ID = UserId.of(UUID.randomUUID());

    @Test
    @DisplayName("MEM-037 a member sent or resent carries no refusal and no wait")
    void aMemberSentOrResent_carriesNoRefusal() {
        assertThat(InvitationResult.sent(USER_ID))
                .isEqualTo(new InvitationResult(USER_ID, InvitationOutcome.SENT, null, null));
        assertThat(InvitationResult.resent(USER_ID))
                .isEqualTo(new InvitationResult(USER_ID, InvitationOutcome.RESENT, null, null));
    }

    @Test
    @DisplayName("MEM-037 a refusal carries its reason, and only the resend limit carries the wait")
    void aRefusal_carriesItsReason() {
        assertThat(InvitationResult.refused(USER_ID, InvitationRefusal.DISABLED))
                .isEqualTo(new InvitationResult(USER_ID, InvitationOutcome.REFUSED, InvitationRefusal.DISABLED, null));
        assertThat(InvitationResult.resendLimitReached(USER_ID, 60))
                .isEqualTo(new InvitationResult(USER_ID, InvitationOutcome.REFUSED,
                        InvitationRefusal.RESEND_LIMIT_REACHED, 60L));
    }
}
