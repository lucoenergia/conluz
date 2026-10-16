package org.lucoenergia.conluz.infrastructure.admin.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberDataView;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.UserScope;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Schema(requiredProperties = {"id", "personalId", "number", "fullName", "address", "email",
        "phoneNumber", "enabled", "isPlatformAdmin", "memberships", "capabilities"})
public class UserResponse {

    private final UUID id;
    private final String personalId;
    @Schema(description = "Member number." + MemberDataSchema.SOURCE, types = {"integer", "null"})
    private final Integer number;
    @Schema(description = "Full name." + MemberDataSchema.SOURCE)
    private final String fullName;
    @Schema(description = "Address." + MemberDataSchema.SOURCE, types = {"string", "null"})
    private final String address;
    @Schema(description = "Email." + MemberDataSchema.SOURCE, types = {"string", "null"})
    private final String email;
    @Schema(description = "Phone number." + MemberDataSchema.SOURCE, types = {"string", "null"})
    private final String phoneNumber;
    private final Boolean enabled;
    private final Boolean isPlatformAdmin;
    private final Map<String, String> memberships;

    @Schema(description = "What the caller may do with this user.")
    private final UserCapabilitiesResponse capabilities;

    public UserResponse(User user, UserCapabilitiesResponse capabilities) {
        this(user, capabilities, UserScope.all());
    }

    /**
     * A user as the given scope lets the caller see them: only the memberships the scope admits are
     * carried. The capabilities must already have been assembled from the user's <em>full</em>
     * memberships — narrowing happens here, after every decision has been taken, so it can never
     * change one.
     */
    public UserResponse(User user, UserCapabilitiesResponse capabilities, UserScope scope) {
        this(user, MemberDataView.ofAccount(user), capabilities, scope);
    }

    /**
     * A user showing the given member data, read from the account or from a member record (MEM-026). Every other
     * field still comes from the account.
     */
    public UserResponse(User user, MemberDataView memberData, UserCapabilitiesResponse capabilities) {
        this(user, memberData, capabilities, UserScope.all());
    }

    public UserResponse(User user, MemberDataView memberData, UserCapabilitiesResponse capabilities, UserScope scope) {
        id = user.getId();
        personalId = user.getPersonalId();
        number = memberData.number();
        fullName = memberData.fullName();
        address = memberData.address();
        email = memberData.email();
        phoneNumber = memberData.phoneNumber();
        enabled = user.isEnabled();
        isPlatformAdmin = user.isPlatformAdmin();
        Map<String, String> membershipMap = new HashMap<>();
        if (user.getMemberships() != null) {
            for (CommunityMembership m : user.getMemberships()) {
                if (!scope.includesMembershipOf(user, m)) {
                    continue;
                }
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
