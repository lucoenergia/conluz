package org.lucoenergia.conluz.domain.admin.community.contact;

import java.util.UUID;

/**
 * Locks a community's row for the rest of the caller's transaction, so that replacements of its contact channels run
 * one after another and the last one wins. Without it, two replacements deleting the same rows at once would both
 * insert their channels, and the second would fail on the position of the first instead of replacing it. It must be
 * called inside a transaction: outside one, the lock would be released at once.
 */
public interface LockCommunityContactChannelsRepository {

    void lock(UUID communityId);
}
