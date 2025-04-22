package org.lucoenergia.conluz.domain.admin.supply.create;


import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;

public interface CreateSupplyService {

    Supply create(Supply supply, UserId id);

    Supply create(Supply supply, UserPersonalId id);
}
