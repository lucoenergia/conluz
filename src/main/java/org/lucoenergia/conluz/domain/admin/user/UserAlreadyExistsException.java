package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.shared.UserId;

public class UserAlreadyExistsException extends RuntimeException {

    private final UserId id;

    public UserAlreadyExistsException(UserId id) {
        this.id = id;
    }

    public UserId getUserId() {
        return id;
    }
}
