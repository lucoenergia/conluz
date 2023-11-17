package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Optional;

public interface GetUserRepository {

    Optional<User> findById(UserId id);

    boolean existsById(UserId id);
}
