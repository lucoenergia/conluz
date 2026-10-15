package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.personalid.NotEmptyOnceNormalised;

@Schema(requiredProperties = {"personalId", "fullName", "contactEmail"})
public class InviteCommunityAdminBody {

    @Schema(description = "The admin's DNI/NIE/NIF. It is normalised before it is stored or compared: whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased, so 12.345.678-a and 12345678A are the same person. A value made only of those removed characters is refused.")
    @NotBlank
    @NotEmptyOnceNormalised
    private String personalId;

    @Schema(description = "The admin's full name, as this community records it.")
    @NotBlank
    private String fullName;

    @Schema(description = "The email address this community contacts the admin at. The invitation is sent to it.")
    @NotBlank
    @Email
    private String contactEmail;

    @Schema(description = "The admin's phone number, as this community records it. An empty or blank value is recorded as none.",
            types = {"string", "null"})
    private String phoneNumber;

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

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UserPersonalId toPersonalId() {
        return UserPersonalId.of(personalId);
    }

    /**
     * The platform admin does not know the admin's member number or address, so the record has neither.
     */
    public MemberRecord toMemberRecord() {
        return new MemberRecord(fullName, null, contactEmail, null, phoneNumber);
    }
}
