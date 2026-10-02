package org.lucoenergia.conluz.infrastructure.admin.user.get;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlatformCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The caller's own record, as returned by {@code GET /api/v1/users/current}.
 *
 * <p>Carries the {@code UserResponse} fields plus two capability objects that answer different
 * questions. {@code capabilities} is the caller acting on <em>their own user</em> — the same object
 * any other response carrying this user would show. {@code platformCapabilities} is about the
 * platform rather than about any user: whether they may create a community, whether they may list
 * users at all.</p>
 *
 * <p>The platform capabilities deliberately live here and not on {@code UserResponse}. They are
 * facts about the caller, and a {@code UserResponse} is frequently somebody else — a supply's owner,
 * a community's member. Putting them on the shared type would invite a client to read another
 * person's record and conclude something about its own permissions.</p>
 *
 * <p>This does not extend {@code UserResponse}: springdoc would then emit the schema as an
 * {@code allOf}, which generated clients handle inconsistently, for no gain over repeating ten
 * accessors.</p>
 */
@Schema(description = "The authenticated caller's own user, with what they may do.",
        requiredProperties = {"id", "personalId", "number", "fullName", "address", "email",
                "phoneNumber", "enabled", "isPlatformAdmin", "memberships", "capabilities",
                "platformCapabilities"})
public class CurrentUserResponse {

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

    @Schema(description = "What the caller may do with their own user record.")
    private final UserCapabilitiesResponse capabilities;

    @Schema(description = "What the caller may do on the platform as a whole, independently of any "
            + "one resource.")
    private final PlatformCapabilitiesResponse platformCapabilities;

    public CurrentUserResponse(User user, UserCapabilitiesResponse capabilities,
                               PlatformCapabilitiesResponse platformCapabilities) {
        this.id = user.getId();
        this.personalId = user.getPersonalId();
        this.number = user.getNumber();
        this.fullName = user.getFullName();
        this.address = user.getAddress();
        this.email = user.getEmail();
        this.phoneNumber = user.getPhoneNumber();
        this.enabled = user.isEnabled();
        this.isPlatformAdmin = user.isPlatformAdmin();
        Map<String, String> membershipMap = new HashMap<>();
        if (user.getMemberships() != null) {
            for (CommunityMembership membership : user.getMemberships()) {
                membershipMap.put(membership.getCommunity().getId().toString(), membership.getRole().name());
            }
        }
        this.memberships = membershipMap;
        this.capabilities = capabilities;
        this.platformCapabilities = platformCapabilities;
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

    public PlatformCapabilitiesResponse getPlatformCapabilities() {
        return platformCapabilities;
    }
}
