package org.lucoenergia.conluz.domain.admin.supply.create;

import java.util.List;
import java.util.UUID;

public interface ImportSuppliesService {

    /**
     * Creates each row's supply in the community, in a transaction of its own, so a row that fails keeps the rows
     * created before and after it.
     *
     * @return one result per row, in the order of the rows
     */
    List<ImportedSupplyRow> importSupplies(UUID communityId, List<SupplyImportRow> rows);
}
