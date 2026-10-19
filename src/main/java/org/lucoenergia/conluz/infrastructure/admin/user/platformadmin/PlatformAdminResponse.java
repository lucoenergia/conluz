package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PlatformAdmin;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlatformAdminCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.UserReferenceResponse;

/**
 * A user holding the platform-admin role, identified by their own account: the name and email of the account and
 * whether it is enabled. No personal id, nothing from a member record and nothing about memberships.
 */
@Schema(description = "A platform admin, identified by their own account.",
        requiredProperties = {"user", "fullName", "email", "enabled", "revocable", "capabilities"})
public class PlatformAdminResponse {

    @Schema(description = "The platform admin's account, by id only: the {userId} the platform-admin endpoints take.")
    private final UserReferenceResponse user;

    @Schema(description = "The platform admin's full name, as their account records it.")
    private final String fullName;

    @Schema(description = "The email address of the platform admin's account. Null if there is none.",
            types = {"string", "null"})
    private final String email;

    @Schema(description = "Whether the account is enabled. A disabled platform admin keeps the role but cannot log in.")
    private final boolean enabled;

    @Schema(description = "Whether revoking this user's platform-admin flag would be accepted right now. False while "
            + "no more than one platform admin is enabled: the revoke is then refused with USER_LAST_PLATFORM_ADMIN. "
            + "Independent of capabilities.canRevoke, which says whether the caller may ask for it.")
    private final boolean revocable;

    @Schema(description = "What the caller may do with this platform admin.")
    private final PlatformAdminCapabilitiesResponse capabilities;

    public PlatformAdminResponse(PlatformAdmin admin, PlatformAdminCapabilitiesResponse capabilities) {
        User user = admin.user();
        this.user = new UserReferenceResponse(user.getId());
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.enabled = user.isEnabled();
        this.revocable = admin.revocable();
        this.capabilities = capabilities;
    }

    public UserReferenceResponse getUser() {
        return user;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRevocable() {
        return revocable;
    }

    public PlatformAdminCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
