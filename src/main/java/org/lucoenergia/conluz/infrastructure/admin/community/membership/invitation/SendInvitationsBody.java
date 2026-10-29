package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * The members a bulk send acts on (#527). Its size and distinctness are rules of the bulk send, checked by the
 * service, so that each is refused with its own code.
 */
@Schema(requiredProperties = {"userIds"})
public class SendInvitationsBody {

    @Schema(description = "The user ids of the members to send or resend the invitation to: 1 to 50, each at "
            + "most once.")
    private List<@NotNull UUID> userIds;

    public List<UUID> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<UUID> userIds) {
        this.userIds = userIds;
    }
}
