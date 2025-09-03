package org.lucoenergia.conluz.domain.admin.supply.disable;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;

public interface DisableSupplyRepository {

    Supply disable(SupplyId id);
}
