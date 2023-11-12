package org.lucoenergia.conluz.domain.admin;

import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.Optional;

public interface GetUserRepository {

    Optional<User> findById(UserId id);
}
