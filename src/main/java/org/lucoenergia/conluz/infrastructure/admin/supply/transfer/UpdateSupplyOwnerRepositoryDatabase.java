package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.lucoenergia.conluz.domain.admin.supply.transfer.UpdateSupplyOwnerRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class UpdateSupplyOwnerRepositoryDatabase implements UpdateSupplyOwnerRepository {

    private final SupplyRepository repository;

    public UpdateSupplyOwnerRepositoryDatabase(SupplyRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean updateOwner(SupplyId supplyId, UserId ownerId) {
        return repository.updateOwner(supplyId.getId(), ownerId.getId()) == 1;
    }
}
