package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;

/**
 * A member's record in one community, under the names {@link MemberResponse} gives it. It carries nothing from the
 * member's account (ADR-0003 §1).
 */
@Schema(description = "A member's record in one community.",
        requiredProperties = {"fullName", "memberNumber", "contactEmail", "address", "phoneNumber"})
public class MemberRecordResponse {

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

    private MemberRecordResponse(CommunityMembership membership) {
        this.fullName = membership.getFullName();
        this.memberNumber = membership.getMemberNumber();
        this.contactEmail = membership.getContactEmail();
        this.address = membership.getAddress();
        this.phoneNumber = membership.getPhoneNumber();
    }

    /**
     * @param membership the member's membership in the community, or {@code null} if they have none
     * @return the record, or {@code null} when there is no membership
     */
    public static MemberRecordResponse ofRecord(CommunityMembership membership) {
        return membership != null ? new MemberRecordResponse(membership) : null;
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
}
