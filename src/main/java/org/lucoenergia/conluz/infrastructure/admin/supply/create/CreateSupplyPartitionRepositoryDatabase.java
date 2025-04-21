package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.*;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyPartitionId;
import org.lucoenergia.conluz.infrastructure.admin.supply.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Repository
public class CreateSupplyPartitionRepositoryDatabase implements CreateSupplyPartitionRepository {

    private final SupplyRepository supplyRepository;
    private final SupplyPartitionRepository supplyPartitionRepository;
    private final SupplyPartitionEntityMapper supplyPartitionEntityMapper;
    private final SharingAgreementRepository sharingAgreementRepository;

    public CreateSupplyPartitionRepositoryDatabase(SupplyRepository supplyRepository,
                                                   SupplyPartitionRepository supplyPartitionRepository,
                                                   SupplyPartitionEntityMapper supplyPartitionEntityMapper,
                                                   SharingAgreementRepository sharingAgreementRepository) {
        this.supplyRepository = supplyRepository;
        this.supplyPartitionRepository = supplyPartitionRepository;
        this.supplyPartitionEntityMapper = supplyPartitionEntityMapper;
        this.sharingAgreementRepository = sharingAgreementRepository;
    }

    @Override
    public SupplyPartition updateCoefficient(SupplyPartitionId id, Double coefficient) {
        Optional<SupplyPartitionEntity> supplyPartitionEntity = supplyPartitionRepository.findById(id.getId());
        if (supplyPartitionEntity.isEmpty()) {
            throw new SupplyPartitionNotFoundException(id);
        }
        SupplyPartitionEntity supplyPartition = supplyPartitionEntity.get();
        supplyPartition.setCoefficient(coefficient);
        supplyPartition = supplyPartitionRepository.save(supplyPartition);
        return supplyPartitionEntityMapper.map(supplyPartition);
    }

    @Override
    public SupplyPartition create(SupplyCode code, Double coefficient, SharingAgreementId id) {
        SupplyPartitionEntity supplyPartition = new SupplyPartitionEntity();
        Optional<SupplyEntity> supplyEntity = supplyRepository.findByCode(code.getCode());
        if (supplyEntity.isEmpty()) {
            throw new SupplyNotFoundException(code);
        }
        supplyPartition.setSupply(supplyEntity.get());

        Optional<SharingAgreementEntity> sharingAgreementEntity = sharingAgreementRepository.findById(id.getId());
        if (sharingAgreementEntity.isEmpty()) {
            throw new SharingAgreementNotFoundException(id);
        }
        supplyPartition.setSharingAgreement(sharingAgreementEntity.get());
        supplyPartition.setCoefficient(coefficient);

        supplyPartition = supplyPartitionRepository.save(supplyPartition);

        return supplyPartitionEntityMapper.map(supplyPartition);
    }
}
