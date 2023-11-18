package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.shared.UserId;

public class UserNotFoundException extends RuntimeException {

    private final UserId id;

    public UserNotFoundException(UserId id) {
        this.id = id;
    }

    public UserId getUserId() {
        return id;
    }
}
