package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.email.EmailAddresses;
import org.lucoenergia.conluz.infrastructure.shared.personalid.NotEmptyOnceNormalised;

@Schema(requiredProperties = {"personalId", "fullName"})
public class RegisterMemberBody {

    @Schema(description = "The member's DNI/NIE/NIF. It is normalised before it is stored or compared: whitespace (including the no-break space), dots and hyphens are removed and letters are upper-cased, so 12.345.678-a and 12345678A are the same person. A value made only of those removed characters is refused.")
    @NotBlank
    @NotEmptyOnceNormalised
    private String personalId;

    @Schema(description = "The member's full name, as this community records it.")
    @NotBlank
    private String fullName;

    @Schema(description = "The member's number in this community. Omit it, or send null, if the community does not record one.",
            types = {"integer", "null"})
    @Min(value = 0)
    private Integer memberNumber;

    @Schema(description = "The email address this community contacts the member at. Registering sends nothing: the invitation to accept the membership is sent to this address with sendInvitation. Omit it, or send null, an empty or a blank string, if there is none.",
            types = {"string", "null"})
    @Email
    private String contactEmail;

    @Schema(description = "The member's address, as this community records it. An empty or blank value is recorded as none.",
            types = {"string", "null"})
    private String address;

    @Schema(description = "The member's phone number, as this community records it. An empty or blank value is recorded as none.",
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

    public Integer getMemberNumber() {
        return memberNumber;
    }

    public void setMemberNumber(Integer memberNumber) {
        this.memberNumber = memberNumber;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        // Before validation, so that only a value that is not blank is checked as an email address
        this.contactEmail = EmailAddresses.blankToNull(contactEmail);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public MemberRecord toMemberRecord() {
        return new MemberRecord(fullName, memberNumber, contactEmail, address, phoneNumber);
    }
}
