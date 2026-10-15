package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.CommunityReferenceResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.UserReferenceResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * A member of a community as that community sees them: the membership, its state and the community's own member
 * record. It carries nothing from the member's account but its id (ADR-0003 §1), which is random, so it reads the same
 * whether or not the person had an account before.
 */
@Schema(description = "A member of a community: the membership, its state and the community's own member record.",
        requiredProperties = {"id", "user", "community", "role", "personalId", "fullName", "memberNumber", "contactEmail",
                "address", "phoneNumber", "acceptedAt", "enabled", "invitedAt", "invitationExpiresAt", "capabilities"})
public class MemberResponse {

    @Schema(description = "The membership's id.")
    private final UUID id;

    @Schema(description = "The member's account, by id only: the {userId} the membership's endpoints take. Nothing "
            + "else of the account is carried.")
    private final UserReferenceResponse user;

    @Schema(description = "The community the membership belongs to.")
    private final CommunityReferenceResponse community;

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

    @Schema(description = "When the membership was accepted. Null while it is pending: a pending membership "
            + "grants no access until it is accepted. Independent of enabled.",
            example = "2026-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant acceptedAt;

    private final Boolean enabled;

    @Schema(description = "When the member was last sent an invitation. Null if none has been sent.",
            example = "2026-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant invitedAt;

    @Schema(description = "When the membership's invitation link expires. Null when it has no link that can still "
            + "be used: none was sent, it was revoked or used, or it has expired.",
            example = "2026-01-08T00:00:00Z", types = {"string", "null"})
    private final Instant invitationExpiresAt;

    @Schema(description = "What the caller may do with this membership.")
    private final MembershipCapabilitiesResponse capabilities;

    /**
     * @param personalId          the personal ID the community knows the member by, normalised
     * @param invitationExpiresAt when the membership's invitation link expires, null when it has none
     */
    public MemberResponse(String personalId, CommunityMembership membership, Instant invitationExpiresAt,
                          MembershipCapabilitiesResponse capabilities) {
        this.id = membership.getId();
        this.user = new UserReferenceResponse(membership.getUser().getId());
        this.community = new CommunityReferenceResponse(membership.getCommunity().getId(),
                membership.getCommunity().getName());
        this.role = membership.getRole();
        this.personalId = personalId;
        this.fullName = membership.getFullName();
        this.memberNumber = membership.getMemberNumber();
        this.contactEmail = membership.getContactEmail();
        this.address = membership.getAddress();
        this.phoneNumber = membership.getPhoneNumber();
        this.acceptedAt = membership.getAcceptedAt();
        this.enabled = membership.isEnabled();
        this.invitedAt = membership.getInvitedAt();
        this.invitationExpiresAt = invitationExpiresAt;
        this.capabilities = capabilities;
    }

    public UUID getId() {
        return id;
    }

    public UserReferenceResponse getUser() {
        return user;
    }

    public CommunityReferenceResponse getCommunity() {
        return community;
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

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getInvitationExpiresAt() {
        return invitationExpiresAt;
    }

    public MembershipCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
