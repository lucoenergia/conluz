package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.user.User;

import java.util.List;

public interface GetSupplyRepositoryDatadis {

    List<DatadisSupply> getSuppliesByUser(User user);
}
