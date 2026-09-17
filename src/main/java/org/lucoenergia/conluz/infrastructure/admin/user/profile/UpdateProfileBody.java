package org.lucoenergia.conluz.infrastructure.admin.user.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.lucoenergia.conluz.domain.admin.user.profile.ContactDetails;

/**
 * The contact details a user may change on their own record. Deliberately narrower than
 * {@code UpdateUserBody}: there is no {@code personalId}, {@code fullName} or {@code number} here,
 * so the self-service path cannot rewrite the fields that identify the member.
 */
@Schema(requiredProperties = {
        "email"
})
public class UpdateProfileBody {

    @NotBlank
    @Email
    private String email;
    private String address;
    private String phoneNumber;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public ContactDetails toContactDetails() {
        return new ContactDetails(email, address, phoneNumber);
    }
}
