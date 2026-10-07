package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Deliberately has no {@code toString}: the personal ID may only reach a log line masked.
 */
@Schema(requiredProperties = {"personalId"})
public class PasswordResetRequestBody {

    @NotNull
    @Schema(description = """
            The personal ID (DNI/NIE/NIF) of the account to recover. Spaces, dots and hyphens are ignored and \
            letters may be in either case, as when logging in.""", example = "12345678Z")
    private String personalId;

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String personalId) {
        this.personalId = personalId;
    }
}
