package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationOutcome;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationRefusal;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationResult;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SendInvitationResultResponseTest {

    private static final UUID COMMUNITY_ID = UUID.randomUUID();
    private static final UserId USER_ID = UserId.of(UUID.randomUUID());

    @ParameterizedTest
    @EnumSource(value = InvitationOutcome.class, names = {"SENT", "RESENT"})
    @DisplayName("MEM-037 a member sent or resent carries the requested user, no code and no params")
    void aMemberSentOrResent_carriesNoCode(InvitationOutcome outcome) {
        InvitationResult result = outcome == InvitationOutcome.SENT
                ? InvitationResult.sent(USER_ID)
                : InvitationResult.resent(USER_ID);

        SendInvitationResultResponse response = new SendInvitationResultResponse(COMMUNITY_ID, result);

        assertThat(response.getUser().getId()).isEqualTo(USER_ID.getId());
        assertThat(response.getOutcome()).isEqualTo(outcome);
        assertThat(response.getCode()).isNull();
        assertThat(response.getParams()).isEmpty();
    }

    @Test
    @DisplayName("MEM-037 each refusal carries the code and params the single send or resend answers")
    void eachRefusal_carriesTheCodeOfTheSingleEndpoints() {
        Map<InvitationRefusal, RestErrorCode> expected = Map.of(
                InvitationRefusal.NOT_A_MEMBER, RestErrorCode.MEMBERSHIP_NOT_FOUND,
                InvitationRefusal.ALREADY_ACCEPTED, RestErrorCode.MEMBERSHIP_ALREADY_EXISTS,
                InvitationRefusal.DISABLED, RestErrorCode.MEMBERSHIP_DISABLED,
                InvitationRefusal.WITHOUT_CONTACT_EMAIL, RestErrorCode.MEMBERSHIP_WITHOUT_CONTACT_EMAIL);
        // Every refusal but the resend limit, tested below with its wait
        assertThat(expected.keySet()).isEqualTo(EnumSet.complementOf(
                EnumSet.of(InvitationRefusal.RESEND_LIMIT_REACHED)));

        expected.forEach((refusal, code) -> {
            SendInvitationResultResponse response = new SendInvitationResultResponse(COMMUNITY_ID,
                    InvitationResult.refused(USER_ID, refusal));

            assertThat(response.getOutcome()).isEqualTo(InvitationOutcome.REFUSED);
            assertThat(response.getCode()).isEqualTo(code);
            assertThat(response.getParams()).isEqualTo(Map.of("userId", USER_ID.getId().toString(),
                    "communityId", COMMUNITY_ID.toString()));
        });
    }

    @Test
    @DisplayName("MEM-037 a refusal for the resend limit also carries the wait")
    void theResendLimit_carriesTheWait() {
        SendInvitationResultResponse response = new SendInvitationResultResponse(COMMUNITY_ID,
                InvitationResult.resendLimitReached(USER_ID, 72_001));

        assertThat(response.getOutcome()).isEqualTo(InvitationOutcome.REFUSED);
        assertThat(response.getCode()).isEqualTo(RestErrorCode.MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED);
        assertThat(response.getParams()).isEqualTo(Map.of("userId", USER_ID.getId().toString(),
                "communityId", COMMUNITY_ID.toString(), "retryAfterSeconds", "72001"));
    }
}
