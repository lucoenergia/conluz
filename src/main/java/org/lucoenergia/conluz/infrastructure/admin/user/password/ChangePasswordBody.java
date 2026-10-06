package org.lucoenergia.conluz.infrastructure.admin.user.password;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Deliberately has no {@code toString}: neither password may ever reach a log line.
 */
@Schema(requiredProperties = {"currentPassword", "newPassword"})
public class ChangePasswordBody {

    @NotNull
    @Schema(description = "The caller's current password, exactly as it was set.")
    private String currentPassword;

    @NotNull
    @Schema(minLength = 15, description = """
            The new password. Between 15 and 64 characters, counting each Unicode code point as one, and \
            no more than 72 bytes once UTF-8 encoded. Any character is accepted, including spaces and \
            non-ASCII letters; there are no composition rules, and the value is never trimmed or \
            transformed. It must differ from the current password; the comparison is exact, so a value that \
            differs only by case or by leading or trailing spaces is a different password.""")
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
