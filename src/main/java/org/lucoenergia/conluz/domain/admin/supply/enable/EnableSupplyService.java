package org.lucoenergia.conluz.domain.admin.supply.enable;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;

public interface EnableSupplyService {

    Supply enable(SupplyId id);
}
