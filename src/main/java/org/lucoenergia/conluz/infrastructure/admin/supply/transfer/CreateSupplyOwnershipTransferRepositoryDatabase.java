package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.lucoenergia.conluz.domain.admin.supply.transfer.CreateSupplyOwnershipTransferRepository;
import org.lucoenergia.conluz.domain.admin.supply.transfer.SupplyOwnershipTransfer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class CreateSupplyOwnershipTransferRepositoryDatabase implements CreateSupplyOwnershipTransferRepository {

    private final SupplyOwnershipTransferJpaRepository repository;
    private final SupplyOwnershipTransferEntityMapper mapper;

    public CreateSupplyOwnershipTransferRepositoryDatabase(SupplyOwnershipTransferJpaRepository repository,
                                                           SupplyOwnershipTransferEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public SupplyOwnershipTransfer create(SupplyOwnershipTransfer transfer) {
        return mapper.map(repository.save(mapper.map(transfer)));
    }
}
