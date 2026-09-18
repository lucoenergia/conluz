package org.lucoenergia.conluz.infrastructure.admin.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Schema(requiredProperties = {"id", "personalId", "number", "fullName", "address", "email",
        "phoneNumber", "enabled", "isPlatformAdmin", "memberships", "capabilities"})
public class UserResponse {

    private final UUID id;
    private final String personalId;
    private final Integer number;
    private final String fullName;
    @Schema(types = {"string", "null"})
    private final String address;
    private final String email;
    @Schema(types = {"string", "null"})
    private final String phoneNumber;
    private final Boolean enabled;
    private final Boolean isPlatformAdmin;
    private final Map<String, String> memberships;

    @Schema(description = "What the caller may do with this user.")
    private final UserCapabilitiesResponse capabilities;

    public UserResponse(User user, UserCapabilitiesResponse capabilities) {
        id = user.getId();
        personalId = user.getPersonalId();
        number = user.getNumber();
        fullName = user.getFullName();
        address = user.getAddress();
        email = user.getEmail();
        phoneNumber = user.getPhoneNumber();
        enabled = user.isEnabled();
        isPlatformAdmin = user.isPlatformAdmin();
        Map<String, String> membershipMap = new HashMap<>();
        if (user.getMemberships() != null) {
            for (CommunityMembership m : user.getMemberships()) {
                membershipMap.put(m.getCommunity().getId().toString(), m.getRole().name());
            }
        }
        memberships = membershipMap;
        this.capabilities = capabilities;
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

    @JsonProperty("isPlatformAdmin")
    public Boolean getIsPlatformAdmin() {
        return isPlatformAdmin;
    }

    public Map<String, String> getMemberships() {
        return memberships;
    }

    public UserCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
