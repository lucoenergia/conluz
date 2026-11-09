package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSupplySelection;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes what Datadis returned for one supply, in a transaction of its own, so that a supply that fails keeps the
 * supplies written before and after it.
 */
@Component
@Transactional
public class DatadisSupplyUpdateStep {

    private final GetSupplyRepository getSupplyRepository;
    private final UpdateSupplyRepository updateSupplyRepository;

    public DatadisSupplyUpdateStep(GetSupplyRepository getSupplyRepository,
                                   UpdateSupplyRepository updateSupplyRepository) {
        this.getSupplyRepository = getSupplyRepository;
        this.updateSupplyRepository = updateSupplyRepository;
    }

    /**
     * @throws SupplyNotFoundException if the supply was deleted since it was read
     */
    public void update(SupplyId supplyId, DatadisSupply datadisSupply) {
        Supply supply = getSupplyRepository.findById(supplyId)
                .orElseThrow(() -> new SupplyNotFoundException(supplyId));

        // An address the community admin entered is kept; Datadis only fills one that is missing
        if (supply.getAddress() == null || supply.getAddress().isBlank()) {
            supply.setAddress(datadisSupply.getAddress());
        }

        supply.setDistributor(new SupplyDistributor.Builder()
                .withCode(datadisSupply.getDistributorCode())
                .withName(datadisSupply.getDistributor())
                .withPointType(datadisSupply.getPointType())
                .build());

        supply.setContract(new SupplyContract.Builder()
                .withValidDateFrom(DatadisSupplySelection.startDate(datadisSupply).orElse(null))
                .build());

        updateSupplyRepository.update(supply);
    }
}
