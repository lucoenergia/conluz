package org.lucoenergia.conluz.domain.admin.community.admins.notification;

/**
 * A change to a community's admins that its admins are told about (#466).
 */
public enum CommunityAdminChange {

    /** The platform admin invited someone to administer the community. */
    INVITED,
    /** The platform admin sent a pending admin's invitation again. */
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
