package org.lucoenergia.conluz.domain.admin.user.platformadmin;


/**
 * A row of the list of platform admins: a user holding the role, or a pending invitation to it (ADR-0004 §7). A
 * pending invitation never holds the role.
 */
public sealed interface PlatformAdminListEntry permits PlatformAdmin, PendingPlatformAdmin {
}
