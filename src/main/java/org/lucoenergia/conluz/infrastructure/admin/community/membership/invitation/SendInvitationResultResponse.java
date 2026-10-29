package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationOutcome;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.InvitationResult;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.UserReferenceResponse;

import java.util.Map;
import java.util.UUID;

/**
 * What a bulk send did with one selected member (#527). A refusal carries the code and the parameters the single
 * send or resend answers for it, so the client explains it the same way.
 */
@Schema(description = "What a bulk send did with one selected member.",
        requiredProperties = {"user", "outcome", "code", "params"})
public class SendInvitationResultResponse {

    @Schema(description = "The selected user, as requested.")
    private final UserReferenceResponse user;
    @Schema(description = "`SENT` if the member was sent their first invitation, as `sendInvitation` does; `RESENT` "
            + "if their invitation was resent, as `resendInvitation` does; `REFUSED` otherwise, for the reason in "
            + "`code`.", example = "SENT")
    private final InvitationOutcome outcome;
    @Schema(description = "Why the member was refused, null unless `outcome` is `REFUSED`: "
            + "`MEMBERSHIP_NOT_FOUND` (the user is not a member of the community), `MEMBERSHIP_ALREADY_EXISTS` (the "
            + "membership is accepted), `MEMBERSHIP_DISABLED` (the pending membership is disabled), "
            + "`MEMBERSHIP_WITHOUT_CONTACT_EMAIL` (the member record has no contact email) or "
            + "`MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED` (sent as many times as allowed in the last 24 hours).",
            types = {"string", "null"})
    private final RestErrorCode code;
    @Schema(description = "The refusal's parameters, as the single send or resend answers them: `userId` and "
            + "`communityId`, and for `MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED` also `retryAfterSeconds`, the "
            + "whole seconds until another resend is allowed. Empty unless `outcome` is `REFUSED`.")
    private final Map<String, String> params;

    public SendInvitationResultResponse(UUID communityId, InvitationResult result) {
        this.user = new UserReferenceResponse(result.userId().getId());
        this.outcome = result.outcome();
        this.code = result.refusal() == null ? null : codeOf(result);
        this.params = result.refusal() == null ? Map.of() : paramsOf(communityId, result);
    }

    private static RestErrorCode codeOf(InvitationResult result) {
        return switch (result.refusal()) {
            case NOT_A_MEMBER -> RestErrorCode.MEMBERSHIP_NOT_FOUND;
            case ALREADY_ACCEPTED -> RestErrorCode.MEMBERSHIP_ALREADY_EXISTS;
            case DISABLED -> RestErrorCode.MEMBERSHIP_DISABLED;
            case WITHOUT_CONTACT_EMAIL -> RestErrorCode.MEMBERSHIP_WITHOUT_CONTACT_EMAIL;
            case RESEND_LIMIT_REACHED -> RestErrorCode.MEMBERSHIP_INVITATION_RESEND_LIMIT_REACHED;
        };
    }

    private static Map<String, String> paramsOf(UUID communityId, InvitationResult result) {
        String userId = result.userId().getId().toString();
        if (result.retryAfterSeconds() != null) {
            return Map.of("userId", userId, "communityId", communityId.toString(),
                    "retryAfterSeconds", String.valueOf(result.retryAfterSeconds()));
        }
        return Map.of("userId", userId, "communityId", communityId.toString());
    }

    public UserReferenceResponse getUser() {
        return user;
    }

    public InvitationOutcome getOutcome() {
        return outcome;
    }

    public RestErrorCode getCode() {
        return code;
    }

    public Map<String, String> getParams() {
        return params;
    }
}
