package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartition;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Repository
public class GetSupplyPartitionRepositoryDatabase implements GetSupplyPartitionRepository {

    private final SupplyPartitionRepository supplyPartitionRepository;
    private final SupplyPartitionEntityMapper supplyPartitionEntityMapper;

    public GetSupplyPartitionRepositoryDatabase(SupplyPartitionRepository supplyPartitionRepository, SupplyPartitionEntityMapper supplyPartitionEntityMapper) {
        this.supplyPartitionRepository = supplyPartitionRepository;
        this.supplyPartitionEntityMapper = supplyPartitionEntityMapper;
    }

    @Override
    public Optional<SupplyPartition> findBySupplyAndSharingAgreement(@NotNull SupplyId supplyId,
                                                                     @NotNull SharingAgreementId agreementId) {
        Optional<SupplyPartitionEntity> entity = supplyPartitionRepository.findBySupplyIdAndSharingAgreementId(supplyId.getId(),
                agreementId.getId());
        return entity.map(supplyPartitionEntityMapper::map);
    }
}
