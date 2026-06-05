package org.lucoenergia.conluz.infrastructure.admin.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.HashMap;
import java.util.Map;
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
    private final Boolean isPlatformAdmin;
    private final Map<String, String> memberships;

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
        isPlatformAdmin = user.isPlatformAdmin();
        Map<String, String> membershipMap = new HashMap<>();
        if (user.getMemberships() != null) {
            for (CommunityMembership m : user.getMemberships()) {
                membershipMap.put(m.getCommunity().getId().toString(), m.getRole().name());
            }
        }
        memberships = membershipMap;
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

    @JsonProperty("isPlatformAdmin")
    public Boolean getIsPlatformAdmin() {
        return isPlatformAdmin;
    }

    public Map<String, String> getMemberships() {
        return memberships;
    }
}
