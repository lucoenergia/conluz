package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.admins.CommunityAdmin;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.UserReferenceResponse;

import java.time.Instant;

/**
 * A community's admin as the platform admin sees them (ADR-0003 §3): the name, contact email and phone from this
 * community's member record, the personal id from the account, and the state of the membership and its invitation.
 * Nothing else from the account, and nothing from any other membership of the same person.
 */
@Schema(description = "A community's admin: this community's member record, the personal id, and the state of the membership and its invitation.",
        requiredProperties = {"user", "personalId", "fullName", "contactEmail", "phoneNumber", "acceptedAt", "enabled",
                "invitedAt", "invitationExpiresAt"})
public class CommunityAdminResponse {

    @Schema(description = "The admin's account, by id only: the {userId} the admin endpoints take.")
    private final UserReferenceResponse user;

    @Schema(description = "The admin's DNI/NIE/NIF, normalised.")
    private final String personalId;

    @Schema(description = "The admin's full name, as this community records it.")
    private final String fullName;

    @Schema(description = "The email address this community contacts the admin at. Null if there is none.",
            types = {"string", "null"})
    private final String contactEmail;

    @Schema(description = "The admin's phone number, as this community records it. Null if there is none.",
            types = {"string", "null"})
    private final String phoneNumber;

    @Schema(description = "When the membership was accepted. Null while it is pending: a pending membership "
            + "grants no access until it is accepted. Independent of enabled.",
            example = "2026-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant acceptedAt;

    @Schema(description = "Whether the membership is enabled. A disabled one grants no access to this community, "
            + "whatever its other state.")
    private final boolean enabled;

    @Schema(description = "When the admin was last sent an invitation. Null if none has been sent.",
            example = "2026-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant invitedAt;

    @Schema(description = "When the membership's invitation link expires. Null when it has no link that can still "
            + "be used: none was sent, it was revoked or used, or it has expired.",
            example = "2026-01-08T00:00:00Z", types = {"string", "null"})
    private final Instant invitationExpiresAt;

    public CommunityAdminResponse(CommunityAdmin admin) {
        CommunityMembership membership = admin.membership();
        this.user = new UserReferenceResponse(membership.getUser().getId());
        this.personalId = admin.personalId();
        this.fullName = membership.getFullName();
        this.contactEmail = membership.getContactEmail();
        this.phoneNumber = membership.getPhoneNumber();
        this.acceptedAt = membership.getAcceptedAt();
        this.enabled = membership.isEnabled();
        this.invitedAt = membership.getInvitedAt();
        this.invitationExpiresAt = admin.invitationExpiresAt();
    }

    public UserReferenceResponse getUser() {
        return user;
    }

    public String getPersonalId() {
        return personalId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getInvitationExpiresAt() {
        return invitationExpiresAt;
    }
}
