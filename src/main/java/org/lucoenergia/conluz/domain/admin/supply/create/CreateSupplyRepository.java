package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.UserId;

public interface CreateSupplyRepository {

    Supply create(Supply supply, UserId id);
}
