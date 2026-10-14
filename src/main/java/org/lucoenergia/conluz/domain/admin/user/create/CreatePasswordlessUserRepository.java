package org.lucoenergia.conluz.domain.admin.user.create;

import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;

/**
 * Creates the account of a person registered by a community admin (ADR-0003 §3): an account without a
 * password, which gets its first one only by accepting an invitation (ADR-0003 §7).
 *
 * <p>Deliberately separate from {@link CreateUserRepository}, which hashes a password through the password
 * policy and refuses an account without one: this port takes no password at all, so no other creation path
 * can store a missing password through it.</p>
 */
public interface CreatePasswordlessUserRepository {

    /**
     * Creates an account without a password, without a number and without having to change its password,
     * unless an account with the same personal id already exists. An existing account is never written.
     * Losing a race against a concurrent creation of the same personal id is not an error: the account the
     * other creation stored is returned.
     *
     * @param personalId the personal id; it is normalised before it is stored or compared
     * @param fullName   the account's name
     * @param email      the account email, or {@code null}; an empty or blank one is stored as {@code null}
     * @return the id of the account with that personal id, created now or before
     */
    UserId createIfAbsent(UserPersonalId personalId, String fullName, String email);
}
