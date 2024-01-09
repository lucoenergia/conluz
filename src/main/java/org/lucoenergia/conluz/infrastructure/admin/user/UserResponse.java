package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

public class UserResponse {

    private final UUID id;
    private final String personalId;
    private final Integer number;
    private final String fullName;
    private final String address;
    private final String email;
    private final String phoneNumber;
    private final Boolean enabled;
    private final Role role;

    public UserResponse(User user) {
        id = user.getId();
        personalId = user.getPersonalId();
        number = user.getNumber();
        fullName = user.getFullName();
        address = user.getAddress();
        email = user.getEmail();
        phoneNumber = user.getPhoneNumber();
        enabled = user.isEnabled();
        role = user.getRole();
    }

    public UUID getId() {
        return id;
    }

    public String getPersonalId() {
        return personalId;
    }

    public Integer getNumber() {
        return number;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAddress() {
        return address;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Role getRole() {
        return role;
    }
}
