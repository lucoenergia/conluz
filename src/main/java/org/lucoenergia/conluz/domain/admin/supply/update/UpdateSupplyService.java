package org.lucoenergia.conluz.domain.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;

public interface UpdateSupplyService {

    Supply update(SupplyId supplyId, UpdateSupplyDto supply);
}
