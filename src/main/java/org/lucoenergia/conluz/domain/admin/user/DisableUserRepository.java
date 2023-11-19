package org.lucoenergia.conluz.domain.admin.user;

import org.lucoenergia.conluz.domain.shared.UserId;

public interface DisableUserRepository {

    void disable(UserId id);
}
