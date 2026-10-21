package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Repository
public class DeleteSupplyRepositoryDatabase implements DeleteSupplyRepository {

    private final SupplyRepository supplyRepository;

    public DeleteSupplyRepositoryDatabase(SupplyRepository supplyRepository) {
        this.supplyRepository = supplyRepository;
    }

    /**
     * The contract, distributor and Shelly rows go with the supply (JPA cascade). Nothing else does: a plant still
     * attached to the supply makes the deletion fail on its foreign key.
     */
    @Override
    public void delete(SupplyId id) {
        SupplyEntity supply = supplyRepository.findById(id.getId())
                .orElseThrow(() -> new SupplyNotFoundException(id));
        supplyRepository.delete(supply);
    }
}
