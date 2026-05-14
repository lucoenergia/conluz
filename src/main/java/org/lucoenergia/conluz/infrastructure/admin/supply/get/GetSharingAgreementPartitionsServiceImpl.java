package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementPartitionsService;
import org.lucoenergia.conluz.domain.admin.supply.get.SupplyPartitionWithComparison;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetSharingAgreementPartitionsServiceImpl implements GetSharingAgreementPartitionsService {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SupplyPartitionRepository supplyPartitionRepository;

    public GetSharingAgreementPartitionsServiceImpl(SharingAgreementRepository sharingAgreementRepository,
                                                    SupplyPartitionRepository supplyPartitionRepository) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.supplyPartitionRepository = supplyPartitionRepository;
    }

    @Override
    public List<SupplyPartitionWithComparison> findPartitions(SharingAgreementId id) {
        SharingAgreementEntity agreement = sharingAgreementRepository.findById(id.getId())
                .orElseThrow(() -> new SharingAgreementNotFoundException(id));

        List<SupplyPartitionEntity> currentPartitions =
                supplyPartitionRepository.findBySharingAgreementId(id.getId());

        Optional<SharingAgreementEntity> previousAgreement = agreement.getStartDate() != null
                ? sharingAgreementRepository.findFirstByEndDate(agreement.getStartDate().minusDays(1))
                : Optional.empty();

        return currentPartitions.stream()
                .map(partition -> buildWithComparison(partition, previousAgreement))
                .toList();
    }

    private SupplyPartitionWithComparison buildWithComparison(SupplyPartitionEntity partition,
                                                               Optional<SharingAgreementEntity> previousAgreement) {
        UUID supplyId = partition.getSupply().getId();
        Double previousCoefficient = previousAgreement
                .flatMap(prev -> supplyPartitionRepository.findBySupplyIdAndSharingAgreementId(supplyId, prev.getId()))
                .map(SupplyPartitionEntity::getCoefficient)
                .orElse(null);

        String userFullName = partition.getSupply().getUser() != null
                ? partition.getSupply().getUser().getFullName()
                : null;

        return new SupplyPartitionWithComparison.Builder()
                .withSupplyId(supplyId)
                .withCups(partition.getSupply().getCode())
                .withSupplyName(partition.getSupply().getName())
                .withAddress(partition.getSupply().getAddress())
                .withUserFullName(userFullName)
                .withCoefficient(partition.getCoefficient())
                .withPreviousCoefficient(previousCoefficient)
                .build();
    }
}
