package org.lucoenergia.conluz.domain.admin.user.disable;

import org.lucoenergia.conluz.domain.shared.UserId;

public interface DisableUserRepository {

    void disable(UserId id);
}
