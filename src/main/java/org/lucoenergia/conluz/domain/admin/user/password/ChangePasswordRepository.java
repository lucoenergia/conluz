package org.lucoenergia.conluz.domain.admin.user.password;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;

public interface ChangePasswordRepository {

    /**
     * Stores an already hashed password, clears the "must change password" flag and records when the change
     * happened. No other column is written.
     */
    void changePassword(UserId userId, String encodedPassword, Instant changedAt);
}
