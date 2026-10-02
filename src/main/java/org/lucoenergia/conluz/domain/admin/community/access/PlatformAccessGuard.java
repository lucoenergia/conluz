package org.lucoenergia.conluz.domain.admin.community.access;

import java.util.UUID;

/**
 * Platform-wide actions — the ones that used to be written as {@code hasRole('PLATFORM_ADMIN')}
 * directly in {@code @PreAuthorize}.
 *
 * <p>None of these methods ever throws. A role check makes no statement about whether any
 * particular object exists, so introducing a visibility gate here would turn today's 403 into a
 * 404 for, say, a community admin calling an update endpoint. Object ids are accepted only so each
 * endpoint's decision has a name and a subject, which is what lets it be reported later as a
 * capability on that object; the id itself is deliberately unused except where noted.</p>
 */
public interface PlatformAccessGuard {

    boolean canCreateCommunity();

    boolean canUpdateCommunity(UUID communityId);

    boolean canEnableCommunity(UUID communityId);

    boolean canDisableCommunity(UUID communityId);

    boolean canGrantPlatformAdmin(UUID userId);

    /**
     * Unlike its siblings this one does read {@code userId}: a platform admin must not be able to
     * strip their own flag, so it absorbs the {@code and !isCurrentUser(#userId)} that used to sit
     * in the SpEL.
     */
    boolean canRevokePlatformAdmin(UUID userId);
}
