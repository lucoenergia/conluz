package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
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
     * The contract, distributor and Shelly rows go with the supply (JPA cascade). The supply is detached from
     * its owner first: the owner's supply collection cascades, and would otherwise keep the supply alive if loaded.
     */
    @Override
    public void delete(SupplyId id) {
        SupplyEntity supply = supplyRepository.findById(id.getId())
                .orElseThrow(() -> new SupplyNotFoundException(id));
        UserEntity owner = supply.getUser();
        if (owner != null) {
            owner.removeSupply(supply);
        }
        supplyRepository.delete(supply);
    }
}
