package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisteredMember;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.shared.web.reference.CommunityReferenceResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * A member of a community as that community sees them: the membership, its state and the community's own member
 * record. It carries nothing from the member's account (ADR-0003 §1), so it reads the same whether or not the person
 * had an account before.
 */
@Schema(description = "A member of a community: the membership, its state and the community's own member record.",
        requiredProperties = {"id", "community", "role", "personalId", "fullName", "memberNumber", "contactEmail",
                "address", "phoneNumber", "acceptedAt", "enabled", "invitedAt", "capabilities"})
public class MemberResponse {

    @Schema(description = "The membership's id.")
    private final UUID id;

    @Schema(description = "The community the membership belongs to.")
    private final CommunityReferenceResponse community;

    private final CommunityRole role;

    @Schema(description = "The member's DNI/NIE/NIF as it was entered, normalised.")
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

    @Schema(description = "What the caller may do with this membership.")
    private final MembershipCapabilitiesResponse capabilities;

    public MemberResponse(RegisteredMember member, MembershipCapabilitiesResponse capabilities) {
        CommunityMembership membership = member.membership();
        this.id = membership.getId();
        this.community = new CommunityReferenceResponse(membership.getCommunity().getId(),
                membership.getCommunity().getName());
        this.role = membership.getRole();
        this.personalId = member.personalId();
        this.fullName = membership.getFullName();
        this.memberNumber = membership.getMemberNumber();
        this.contactEmail = membership.getContactEmail();
        this.address = membership.getAddress();
        this.phoneNumber = membership.getPhoneNumber();
        this.acceptedAt = membership.getAcceptedAt();
        this.enabled = membership.isEnabled();
        this.invitedAt = membership.getInvitedAt();
        this.capabilities = capabilities;
    }

    public UUID getId() {
        return id;
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

    public MembershipCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
