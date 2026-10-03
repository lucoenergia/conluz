package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Optional;

public class UserNotFoundException extends RuntimeException {

    private Optional<UserId> id = Optional.empty();
    private boolean lookedUpByPersonalId = false;

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

    /**
     * No user has the personal ID that was looked up. The personal ID itself is deliberately not
     * kept, so reporting this error can never echo it back: answering "user X does not exist" to
     * a caller who typed X would let them probe which personal IDs are registered.
     */
    public static UserNotFoundException forPersonalId() {
        UserNotFoundException exception = new UserNotFoundException();
        exception.lookedUpByPersonalId = true;
        return exception;
    }

    public boolean isLookedUpByPersonalId() {
        return lookedUpByPersonalId;
    }

    public String getId() {
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
}
