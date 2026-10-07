package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Deliberately has no {@code toString}: neither the token nor the password may ever reach a log line.
 */
@Schema(requiredProperties = {"token", "newPassword"})
public class PasswordResetBody {

    @NotNull
    @Schema(description = """
            The token from the password reset link: everything after the `#` of \
            `<web client>/reset-password#<token>`, exactly as it appears there.""")
    private String token;

    @NotNull
    @Schema(minLength = 15, description = """
            The new password. Between 15 and 64 characters, counting each Unicode code point as one, and \
            no more than 72 bytes once UTF-8 encoded. Any character is accepted, including spaces and \
            non-ASCII letters; there are no composition rules, and the value is never trimmed or \
            transformed. It must differ from the current password.""")
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
