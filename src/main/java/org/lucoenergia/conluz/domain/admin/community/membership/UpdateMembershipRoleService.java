package org.lucoenergia.conluz.domain.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

public interface UpdateMembershipRoleService {

    /**
     * @param updatedBy the community admin changing the role
     * @return the updated membership, with when its invitation link expires: a pending membership can have a live
     * link while its role changes; and whether it is now the community's last active admin
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException if the membership
     *         is given the admin role and its member record has no contact email. Nothing is changed
     * @throws org.lucoenergia.conluz.domain.admin.community.MembershipLastActiveAdminException if the membership is
     *         demoted and is the community's last active admin ({@link LastActiveCommunityAdminRule}). Nothing is
     *         changed
     */
    MembershipInRoster updateRole(UUID communityId, UUID userId, CommunityRole role, UserId updatedBy);
}
