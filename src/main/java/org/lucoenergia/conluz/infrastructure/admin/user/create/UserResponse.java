package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;

public class UserResponse {

    private String id;
    private Integer number;
    private String fullName;
    private String address;
    private String email;
    private String phoneNumber;
    private Boolean enabled;
    private Role role;

    public UserResponse(User user) {
        id = user.getId();
        number = user.getNumber();
        fullName = user.getFullName();
        address = user.getAddress();
        email = user.getEmail();
        phoneNumber = user.getPhoneNumber();
        enabled = user.isEnabled();
        role = user.getRole();
    }

    public String getId() {
        return id;
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
