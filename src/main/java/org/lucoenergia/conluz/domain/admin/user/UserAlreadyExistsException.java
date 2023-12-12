package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.shared.UserPersonalId;

public class UserAlreadyExistsException extends RuntimeException {

    private final UserPersonalId id;

    public UserAlreadyExistsException(UserPersonalId id) {
        this.id = id;
    }

    public UserPersonalId getUserId() {
        return id;
    }
}
