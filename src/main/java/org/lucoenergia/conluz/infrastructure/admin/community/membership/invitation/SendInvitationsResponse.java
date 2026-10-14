package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SentInvitations;

@Schema(description = "The outcome of sending first invitations in bulk.", requiredProperties = {"sent", "remaining"})
public class SendInvitationsResponse {

    @Schema(description = "How many members were sent their first invitation by this request.", example = "50")
    private final int sent;
    @Schema(description = "How many members of the community are still awaiting their first invitation: pending, "
            + "enabled, with a contact email and never invited. Call again while it is above zero.", example = "70")
    private final long remaining;

    public SendInvitationsResponse(SentInvitations sentInvitations) {
        this.sent = sentInvitations.sent();
        this.remaining = sentInvitations.remaining();
    }

    public int getSent() {
        return sent;
    }

    public long getRemaining() {
        return remaining;
    }
}
