package org.lucoenergia.conluz.domain.admin.user.platformadmin;

/**
 * Locks the set of platform admins for the rest of the caller's transaction, so that the flows that can remove an
 * enabled platform admin (revoking the role, deleting the account, disabling it) run one after another, and each
 * counts the enabled platform admins the previous one left (ACC-013). It must be called inside a transaction:
 * outside one, the lock would be released at once.
 *
 * <p>The last-platform-admin rule depends on every platform admin, not on one row, so no row lock can protect it.
 * This lock is taken first, before the target is read and before any community, user, membership or token row is
 * locked: platform-admin set, then community row, then user row, then membership row, then token rows (ADR-0003).
 * Flows that can only add an enabled platform admin, such as accepting a platform-admin invitation or enabling an
 * account, do not take it: a count that misses one of them is lower, never higher, than the real one.</p>
 */
public interface LockPlatformAdminsRepository {

    void lock();
}
