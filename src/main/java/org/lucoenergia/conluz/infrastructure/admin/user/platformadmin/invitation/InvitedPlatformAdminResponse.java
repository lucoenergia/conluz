package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.PlatformAdminResponse;

/**
 * A platform-admin invitation just sent, and its confirmation code: the only response that ever carries the code
 * (ADR-0004 §4). Deliberately has no {@code toString}.
 */
@Schema(description = "A platform-admin invitation just sent, with the confirmation code generated for it.",
        requiredProperties = {"platformAdmin", "confirmationCode"})
public class InvitedPlatformAdminResponse {

    @Schema(description = "The invitation's row, as the list of platform admins shows it.")
    private final PlatformAdminResponse platformAdmin;

    @Schema(description = "The confirmation code the person will need to accept: 8 characters from 0-9 and A-Z "
            + "without I, L, O and U. Shown only in this response, never again by any endpoint, and never sent by "
            + "email: give it to the person by another channel, such as by phone or in person.",
            example = "7K3MQ9XD")
    private final String confirmationCode;

    public InvitedPlatformAdminResponse(PlatformAdminResponse platformAdmin, String confirmationCode) {
        this.platformAdmin = platformAdmin;
        this.confirmationCode = confirmationCode;
    }

    public PlatformAdminResponse getPlatformAdmin() {
        return platformAdmin;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }
}
