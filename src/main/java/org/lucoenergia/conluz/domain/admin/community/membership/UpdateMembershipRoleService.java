package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;

import java.util.UUID;

public interface UpdateMembershipRoleService {

    /**
     * @return the updated membership, with when its invitation link expires: a pending membership can have a live
     * link while its role changes
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException if the membership
     *         is given the admin role and its member record has no contact email. Nothing is changed
     */
    MembershipWithInvitation updateRole(UUID communityId, UUID userId, CommunityRole role);
}
