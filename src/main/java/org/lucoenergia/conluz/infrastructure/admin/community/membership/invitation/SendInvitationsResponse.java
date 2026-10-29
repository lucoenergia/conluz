package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SentInvitations;

import java.util.List;
import java.util.UUID;

@Schema(description = "The outcome of sending invitations in bulk.",
        requiredProperties = {"sent", "remaining", "results"})
public class SendInvitationsResponse {

    @Schema(description = "How many members were sent their first invitation by this request.", example = "50")
    private final int sent;
    @Schema(description = "How many members of the community are still awaiting their first invitation: pending, "
            + "enabled, with a contact email and never invited. Call again while it is above zero.", example = "70")
    private final long remaining;
    @Schema(description = "With a selection of `userIds`, one result per selected user, in the order requested. "
            + "Empty without one.")
    private final List<SendInvitationResultResponse> results;

    public SendInvitationsResponse(UUID communityId, SentInvitations sentInvitations) {
        this.sent = sentInvitations.sent();
        this.remaining = sentInvitations.remaining();
        this.results = sentInvitations.results().stream()
                .map(result -> new SendInvitationResultResponse(communityId, result))
                .toList();
    }

    public int getSent() {
        return sent;
    }

    public long getRemaining() {
        return remaining;
    }

    public List<SendInvitationResultResponse> getResults() {
        return results;
    }
}
