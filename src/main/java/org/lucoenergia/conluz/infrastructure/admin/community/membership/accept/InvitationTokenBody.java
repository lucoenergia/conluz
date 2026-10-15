package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Deliberately has no {@code toString}: the token may never reach a log line.
 */
@Schema(requiredProperties = {"token"})
public class InvitationTokenBody {

    @NotNull
    @Schema(description = """
            The token from the invitation link: everything after the `#` of \
            `<web client>/accept-invitation#<token>`, exactly as it appears there.""")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
