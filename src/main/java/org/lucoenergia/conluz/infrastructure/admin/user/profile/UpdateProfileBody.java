package org.lucoenergia.conluz.infrastructure.admin.user.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import org.lucoenergia.conluz.domain.admin.user.profile.ContactDetails;
import org.lucoenergia.conluz.domain.shared.email.EmailAddresses;

/**
 * The contact details a user may change on their own record. There is deliberately no
 * {@code personalId}, {@code fullName} or {@code number} here, so the self-service path cannot rewrite
 * the fields that identify the member.
 */
public class UpdateProfileBody {

    /**
     * Optional. A missing, empty or blank value means no email address; anything else must be a valid one.
     */
    @Email
    private String email;
    private String address;
    private String phoneNumber;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        // Before validation, so that only a value that is not blank is checked as an email address
        this.email = EmailAddresses.blankToNull(email);
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
