package org.lucoenergia.conluz.infrastructure.admin.supply.transfer;

import org.lucoenergia.conluz.domain.admin.supply.transfer.GetSupplyOwnershipTransferRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class GetSupplyOwnershipTransferRepositoryDatabase implements GetSupplyOwnershipTransferRepository {

    private final SupplyOwnershipTransferJpaRepository repository;

    public GetSupplyOwnershipTransferRepositoryDatabase(SupplyOwnershipTransferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<LocalDate> findLatestEffectiveDate(SupplyId supplyId) {
        return repository.findLatestEffectiveDateBySupplyId(supplyId.getId());
    }
}
