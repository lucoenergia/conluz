package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;

import java.util.List;

public interface GetSupplyRepositoryDatadis {

    /**
     * The supplies Datadis returns for the given owner, requested with the community's configuration and a token
     * already obtained with it.
     *
     * @return the supplies, or an empty list when Datadis does not answer them
     */
    List<DatadisSupply> getSuppliesByUser(DatadisConfig config, String bearerToken, User user);
}
