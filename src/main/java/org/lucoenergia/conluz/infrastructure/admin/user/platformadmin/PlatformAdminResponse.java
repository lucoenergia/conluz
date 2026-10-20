package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PlatformAdmin;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.PlatformAdminCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.UserReferenceResponse;

/**
 * A row of the list of platform admins: a user holding the role, identified by their own account, the name and email
 * of the account and whether it is enabled; or a pending invitation to the role, with the name and email the inviter
 * typed and nothing from the account, so that it is the same whether or not the account existed (ADR-0004 §7). No
 * personal id, nothing from a member record and nothing about memberships.
 */
@Schema(description = "A platform admin, identified by their own account, or a pending invitation to the role.",
        requiredProperties = {"user", "fullName", "email", "enabled", "revocable", "invitation", "capabilities"})
public class PlatformAdminResponse {

    @Schema(description = "The account, by id only: the {userId} the platform-admin endpoints take.")
    private final UserReferenceResponse user;

    @Schema(description = "The platform admin's full name, as their account records it. For a pending invitation, the "
            + "full name the inviter typed.")
    private final String fullName;

    @Schema(description = "The email address of the platform admin's account. Null if there is none. For a pending "
            + "invitation, the address the invitation was sent to.",
            types = {"string", "null"})
    private final String email;

    @Schema(description = "Whether the account is enabled. A disabled platform admin keeps the role but cannot log in. "
            + "Null for a pending invitation, which tells nothing about the account.",
            types = {"boolean", "null"})
    private final Boolean enabled;

    @Schema(description = "Whether revoking this user's platform-admin flag would be accepted right now. False only "
            + "for the last enabled platform admin, whose revoke is refused with USER_LAST_PLATFORM_ADMIN; a disabled "
            + "platform admin is always revocable. Independent of capabilities.canRevoke, which says whether the caller may ask for it. "
            + "Null for a pending invitation, which holds no role to revoke.",
            types = {"boolean", "null"})
    private final Boolean revocable;

    @Schema(description = "The pending invitation to the role. Null for a user holding the role. A pending invitation "
            + "never counts as a platform admin.",
            types = {"object", "null"})
    private final PlatformAdminInvitationResponse invitation;

    @Schema(description = "What the caller may do with this platform admin.")
    private final PlatformAdminCapabilitiesResponse capabilities;

    public PlatformAdminResponse(PlatformAdmin admin, PlatformAdminCapabilitiesResponse capabilities) {
        User user = admin.user();
        this.user = new UserReferenceResponse(user.getId());
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.enabled = user.isEnabled();
        this.revocable = admin.revocable();
        this.invitation = null;
        this.capabilities = capabilities;
    }

    public PlatformAdminResponse(PendingPlatformAdmin pending, PlatformAdminCapabilitiesResponse capabilities) {
        this.user = new UserReferenceResponse(pending.userId());
        this.fullName = pending.fullName();
        this.email = pending.email();
        this.enabled = null;
        this.revocable = null;
        this.invitation = new PlatformAdminInvitationResponse(pending);
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

    public Boolean getEnabled() {
        return enabled;
    }

    public Boolean getRevocable() {
        return revocable;
    }

    public PlatformAdminInvitationResponse getInvitation() {
        return invitation;
    }

    public PlatformAdminCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
