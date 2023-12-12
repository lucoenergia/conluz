package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Optional;

public class UserNotFoundException extends RuntimeException {

    private final Optional<UserId> id;

    public UserNotFoundException() {
        this.id = Optional.empty();
    }
    public UserNotFoundException(UserId id) {
        this.id = Optional.of(id);
    }

    public Optional<UserId> getUserUuid() {
        return id;
    }
}
