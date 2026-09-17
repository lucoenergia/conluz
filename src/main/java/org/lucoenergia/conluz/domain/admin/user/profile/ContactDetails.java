package org.lucoenergia.conluz.domain.admin.user.profile;

import java.util.Objects;

/**
 * The parts of their own record a user may change without being an administrator: how the community
 * reaches them.
 *
 * <p>Name, DNI and member number are deliberately absent. They identify the member to the community
 * and to the distributor, so changing them is an administrative act performed through
 * {@code PUT /users/{userId}} — not something a member does to themselves.</p>
 */
public class ContactDetails {

    private final String email;
    private final String address;
    private final String phoneNumber;

    public ContactDetails(String email, String address, String phoneNumber) {
        this.email = email;
        this.address = address;
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContactDetails other)) return false;
        return Objects.equals(email, other.email)
                && Objects.equals(address, other.address)
                && Objects.equals(phoneNumber, other.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, address, phoneNumber);
    }
}
