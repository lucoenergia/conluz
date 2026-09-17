package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;

import java.util.Optional;

public class UserNotFoundException extends RuntimeException {

    private Optional<UserId> id = Optional.empty();
    private Optional<UserPersonalId> personalId = Optional.empty();

    public UserNotFoundException() {
        this.id = Optional.empty();
    }

    public UserNotFoundException(String message) {
        super(message);
        this.id = Optional.empty();
    }

    public UserNotFoundException(UserId id) {
        this.id = Optional.of(id);
    }

    public UserNotFoundException(UserPersonalId id) {
        this.personalId = Optional.of(id);
    }

    public String getId() {
        if (personalId.isPresent()) {
            return personalId.get().getPersonalId();
        }
        if (id.isPresent()) {
            // String.valueOf: UserId.of(null) is legal, and this string goes straight into the 404
            // body. A dereference here would answer 500 where the caller asked for a missing user.
            return String.valueOf(id.get().getId());
        }
        return "";
    }

    public Optional<UserId> getUserId() {
        return id;
    }

    public Optional<UserPersonalId> getUserPersonalId() {
        return personalId;
    }
}
