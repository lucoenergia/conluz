package org.lucoenergia.conluz.domain.admin.user.delete;

import org.lucoenergia.conluz.domain.shared.UserId;

public interface DeleteUserRepository {

    void delete(UserId id);
}
