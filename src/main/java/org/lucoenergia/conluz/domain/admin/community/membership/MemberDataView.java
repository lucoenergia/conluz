package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;

/**
 * The member data a response shows about a user: full name, number, email, address and phone, read either from
 * the account or from one member record (MEM-026). Values are carried exactly as stored, unlike
 * {@link MemberRecord}, which normalises what a community enters.
 */
public record MemberDataView(String fullName, Integer number, String email, String address, String phoneNumber) {

    private static final MemberDataView ABSENT = new MemberDataView("", null, null, null, null);

    public static MemberDataView ofAccount(User user) {
        return new MemberDataView(user.getFullName(), user.getNumber(), user.getEmail(), user.getAddress(),
                user.getPhoneNumber());
    }

    public static MemberDataView ofRecord(CommunityMembership membership) {
        return new MemberDataView(membership.getFullName(), membership.getMemberNumber(), membership.getContactEmail(),
                membership.getAddress(), membership.getPhoneNumber());
    }

    /**
     * No member data: there is no member record to read, and the account must not be read instead. The full name
     * is empty rather than null because every response declares it as a string.
     */
    public static MemberDataView absent() {
        return ABSENT;
    }
}
