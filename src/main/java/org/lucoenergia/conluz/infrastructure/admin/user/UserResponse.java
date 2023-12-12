package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.UUID;

public class UserResponse {

    private UUID id;
    private String personalId;
    private Integer number;
    private String fullName;
    private String address;
    private String email;
    private String phoneNumber;
    private Boolean enabled;
    private Role role;

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
