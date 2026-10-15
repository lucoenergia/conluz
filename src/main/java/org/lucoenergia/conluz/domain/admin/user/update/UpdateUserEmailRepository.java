package org.lucoenergia.conluz.domain.admin.user.update;

import org.lucoenergia.conluz.domain.shared.UserId;

public interface UpdateUserEmailRepository {

    /**
     * Sets the account's email. No other column is written.
     *
     * @param email the new email, never blank: "no email" is recorded as {@code null} (#389)
     * @throws org.lucoenergia.conluz.domain.admin.user.UserNotFoundException if there is no such user
     */
    void setEmail(UserId userId, String email);
}
