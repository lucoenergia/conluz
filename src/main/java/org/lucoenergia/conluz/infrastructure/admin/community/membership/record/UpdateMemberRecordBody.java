package org.lucoenergia.conluz.infrastructure.admin.community.membership.record;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.shared.email.EmailAddresses;

/**
 * The whole member record a community keeps on a membership. It replaces the current one: an optional value that is
 * missing is cleared. There is no personal ID here: it identifies the account, which this record never writes.
 */
@Schema(requiredProperties = {"fullName"})
public class UpdateMemberRecordBody {

    @Schema(description = "The member's full name, as this community records it.")
    @NotBlank
    private String fullName;

    @Schema(description = "The member's number in this community. Omit it, or send null, if the community does not record one; a number already recorded is then cleared.",
            types = {"integer", "null"})
    @Min(value = 0)
    private Integer memberNumber;

    @Schema(description = "The email address this community contacts the member at, and where the next invitation is sent. Omit it, or send null, an empty or a blank string, if there is none; one already recorded is then cleared. A membership with the COMMUNITY_ADMIN role must keep one.",
            types = {"string", "null"})
    @Email
    private String contactEmail;

    @Schema(description = "The member's address, as this community records it. Omit it, or send null, an empty or a blank string, if there is none; one already recorded is then cleared.",
            types = {"string", "null"})
    private String address;

    @Schema(description = "The member's phone number, as this community records it. Omit it, or send null, an empty or a blank string, if there is none; one already recorded is then cleared.",
            types = {"string", "null"})
    private String phoneNumber;

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

    public MemberRecord toMemberRecord() {
        return new MemberRecord(fullName, memberNumber, contactEmail, address, phoneNumber);
    }
}
