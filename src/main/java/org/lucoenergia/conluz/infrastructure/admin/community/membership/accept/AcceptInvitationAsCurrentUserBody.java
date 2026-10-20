package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Deliberately has no {@code toString}: neither the token nor the confirmation code may ever reach a log line.
 */
@Schema(requiredProperties = {"token"})
public class AcceptInvitationAsCurrentUserBody {

    @NotNull
    @Schema(description = """
            The token from the invitation link: everything after the `#` of \
            `<web client>/accept-invitation#<token>`, exactly as it appears there.""")
    private String token;

    @Schema(description = """
            The confirmation code of a platform-admin invitation, as whoever invited gave it: 8 characters, in \
            any case, with or without spaces and hyphens. Required when `inspectInvitation` answers \
            `confirmationCodeRequired`; ignored for a community invitation.""",
            types = {"string", "null"})
    private String confirmationCode;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode(String confirmationCode) {
        this.confirmationCode = confirmationCode;
    }
}
