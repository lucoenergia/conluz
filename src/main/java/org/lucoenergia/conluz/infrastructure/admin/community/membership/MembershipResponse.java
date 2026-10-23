package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipInRoster;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.UserReferenceResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * A membership as its community sees it: the membership, its state and the community's own member record (#448). It
 * carries nothing from the member's account but its id and personal ID (ADR-0003 §1).
 */
@Schema(requiredProperties = {"id", "user", "communityId", "role", "personalId", "fullName", "memberNumber",
        "contactEmail", "address", "phoneNumber", "enabled", "acceptedAt", "invitedAt", "invitationExpiresAt",
        "lastActiveAdmin", "capabilities"})
public class MembershipResponse {

    private final UUID id;
    @Schema(description = "The member's account, by id only: the {userId} the membership's endpoints take. Nothing "
            + "else of the account is carried.")
    private final UserReferenceResponse user;
    private final UUID communityId;
    private final CommunityRole role;
    @Schema(description = "The member's DNI/NIE/NIF, normalised: the one the community registered them with.")
    private final String personalId;
    @Schema(description = "The member's full name, as this community records it.")
    private final String fullName;
    @Schema(description = "The member's number in this community. Null if the community records none.",
            types = {"integer", "null"})
    private final Integer memberNumber;
    @Schema(description = "The email address this community contacts the member at. Null if there is none.",
            types = {"string", "null"})
    private final String contactEmail;
    @Schema(description = "The member's address, as this community records it. Null if there is none.",
            types = {"string", "null"})
    private final String address;
    @Schema(description = "The member's phone number, as this community records it. Null if there is none.",
            types = {"string", "null"})
    private final String phoneNumber;
    private final Boolean enabled;
    @Schema(description = "When the membership was accepted. Null while it is pending: a pending membership "
            + "grants no access until it is accepted. Independent of enabled.",
            example = "2026-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant acceptedAt;
    @Schema(description = "When the membership was last sent an invitation. Null if none has been sent.",
            example = "2026-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant invitedAt;
    @Schema(description = "When the membership's invitation link expires. Null when it has no link that can still "
            + "be used: none was sent, it was revoked or used, or it has expired.",
            example = "2026-01-08T00:00:00Z", types = {"string", "null"})
    private final Instant invitationExpiresAt;
    @Schema(description = "Whether demoting this membership to member, or deleting it, would be refused right now "
            + "with MEMBERSHIP_LAST_ACTIVE_ADMIN: true only for the community's last active admin. A pending or "
            + "suspended admin, or one whose account is disabled, is never the last one. Independent of "
            + "capabilities.canUpdateRole and capabilities.canDelete, which say whether the caller may ask for it.")
    private final boolean lastActiveAdmin;

    @Schema(description = "What the caller may do with this membership.")
    private final MembershipCapabilitiesResponse capabilities;

    public MembershipResponse(MembershipInRoster member, MembershipCapabilitiesResponse capabilities) {
        CommunityMembership membership = member.membership();
        this.id = membership.getId();
        this.user = new UserReferenceResponse(membership.getUser().getId());
        this.communityId = membership.getCommunity().getId();
        this.role = membership.getRole();
        this.personalId = membership.getUser().getPersonalId();
        this.fullName = membership.getFullName();
        this.memberNumber = membership.getMemberNumber();
        this.contactEmail = membership.getContactEmail();
        this.address = membership.getAddress();
        this.phoneNumber = membership.getPhoneNumber();
        this.enabled = membership.isEnabled();
        this.acceptedAt = membership.getAcceptedAt();
        this.invitedAt = membership.getInvitedAt();
        this.invitationExpiresAt = member.invitationExpiresAt();
        this.lastActiveAdmin = member.lastActiveAdmin();
        this.capabilities = capabilities;
    }

    public UUID getId() {
        return id;
    }

    public UserReferenceResponse getUser() {
        return user;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public CommunityRole getRole() {
        return role;
    }

    public String getPersonalId() {
        return personalId;
    }

    public String getFullName() {
        return fullName;
    }

    public Integer getMemberNumber() {
        return memberNumber;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getAddress() {
        return address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getInvitationExpiresAt() {
        return invitationExpiresAt;
    }

    public boolean isLastActiveAdmin() {
        return lastActiveAdmin;
    }

    public MembershipCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
