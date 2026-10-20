package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.personalid.NotEmptyOnceNormalised;

/**
 * Deliberately has no {@code toString}: neither the personal id nor the email may reach a log line.
 */
@Schema(requiredProperties = {"personalId", "fullName", "email"})
public class InvitePlatformAdminBody {

    @Schema(description = "The person's DNI/NIE/NIF. It is normalised before it is stored or compared: whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased, so 12.345.678-a and 12345678A are the same person. A value made only of those removed characters is refused.")
    @NotBlank
    @NotEmptyOnceNormalised
    private String personalId;

    @Schema(description = "The person's full name, as the invitation shows it until it is accepted. An existing account keeps its own name.")
    @NotBlank
    private String fullName;

    @Schema(description = "The email address the invitation is sent to. An existing account keeps its own email; one without an email gets this one when the invitation is accepted.")
    @NotBlank
    @Email
    private String email;

    public String getPersonalId() {
        return personalId;
    }

    public void setPersonalId(String personalId) {
        this.personalId = personalId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserPersonalId toPersonalId() {
        return UserPersonalId.of(personalId);
    }
}
