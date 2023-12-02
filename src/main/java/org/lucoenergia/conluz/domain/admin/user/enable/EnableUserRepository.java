package org.lucoenergia.conluz.domain.admin.user.enable;

import org.lucoenergia.conluz.domain.shared.UserId;

public interface EnableUserRepository {

    void enable(UserId id);
}
