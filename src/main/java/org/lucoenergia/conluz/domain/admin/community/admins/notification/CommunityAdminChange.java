package org.lucoenergia.conluz.domain.admin.community.admins.notification;

/**
 * A change to a community's admins that its admins are told about (#466).
 */
public enum CommunityAdminChange {

    /**
     * Someone was invited to administer the community: by the platform admin, or by a community admin sending a
     * pending admin's first invitation through the member endpoints (#482).
     */
    INVITED,
    /**
     * A pending admin's invitation was sent again: by the platform admin, or by a community admin through the member
     * endpoints (#482).
     */
    INVITATION_RESENT,
    /** A pending admin accepted their invitation. */
    ACCEPTED,
    /** A member was given the admin role. */
    PROMOTED,
    /** An admin was given the member role. */
    DEMOTED,
    /** An admin's membership was deleted. */
    REMOVED,
    /** The platform admin suspended an admin's membership. */
    SUSPENDED,
    /** The platform admin reactivated a suspended admin's membership. */
    REACTIVATED
}
