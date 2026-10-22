package org.lucoenergia.conluz.domain.admin.user.create;


import org.lucoenergia.conluz.domain.admin.user.User;

public interface CreateUserService {

    /**
     * Creates a user whose password was chosen by the user themselves, so it is not flagged as having to be
     * changed. Only the initial admin created by {@code /init} is created this way.
     */
    User create(User user);
}
