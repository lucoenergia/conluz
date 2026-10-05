package org.lucoenergia.conluz.domain.admin.user.disable;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.time.Instant;

public interface DisableUserRepository {

    /**
     * Disables the user and records {@code disabledAt} as the moment of their last disable, replacing any
     * earlier one.
     */
    void disable(UserId id, Instant disabledAt);
}
