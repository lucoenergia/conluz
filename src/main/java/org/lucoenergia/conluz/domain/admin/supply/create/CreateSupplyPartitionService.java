package org.lucoenergia.conluz.domain.admin.supply.create;


import org.lucoenergia.conluz.domain.admin.supply.*;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.admin.supply.SupplyPartitionId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyPartitionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

@Transactional
@Service
public class CreateSupplyPartitionService {

    private final CreateSupplyPartitionRepository repository;
    private final GetSupplyPartitionRepository getSupplyPartitionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;

    public CreateSupplyPartitionService(CreateSupplyPartitionRepository repository,
                                        GetSupplyPartitionRepository getSupplyPartitionRepository,
                                        GetSupplyRepository getSupplyRepository,
                                        GetSharingAgreementRepository getSharingAgreementRepository) {
        this.repository = repository;
        this.getSupplyPartitionRepository = getSupplyPartitionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
    }
    
    public void validateTotalCoefficient(Collection<CreateSupplyPartitionDto> suppliesPartitions) {
        double totalCoefficient = calculateTotalCoefficient(suppliesPartitions);
        if (Math.abs(totalCoefficient - 1.0) > 0.000001) {
            throw new IllegalArgumentException("Sum of all coefficients must be equal to 1.");
        }
    }

    private double calculateTotalCoefficient(Collection<CreateSupplyPartitionDto> suppliesPartitions) {
        return suppliesPartitions.stream()
                .mapToDouble(CreateSupplyPartitionDto::getCoefficient)
                .sum();
    }

    public SupplyPartition create(SupplyCode code, Double coefficient, SharingAgreementId sharingAgreementId) {
        // Verify if supply exists
        Optional<Supply> supply = getSupplyRepository.findByCode(code);
        if (supply.isEmpty()) {
            throw new SupplyNotFoundException(code);
        }
        // Verify if sharing agreement exists
        Optional<SharingAgreement> agreement = getSharingAgreementRepository.findById(sharingAgreementId);
        if (agreement.isEmpty()) {
            throw new SharingAgreementNotFoundException(sharingAgreementId);
        }
        // Verify if coefficient is valid
        validateCoefficient(coefficient);

        Optional<SupplyPartition> partition = getSupplyPartitionRepository.findBySupplyAndSharingAgreement(SupplyId.of(supply.get().getId()),
                sharingAgreementId);
        if (partition.isPresent()) {
            return repository.updateCoefficient(SupplyPartitionId.of(partition.get().getId()), coefficient);
        }
        return repository.create(code, coefficient, sharingAgreementId);
    }

    private static void validateCoefficient(Double coefficient) {
        if (coefficient <= 0 || coefficient >= 1) {
            throw new IllegalArgumentException("Coefficient must be between 0 and 1 exclusively.");
        }
        if (BigDecimal.valueOf(coefficient).scale() != 6) {
            throw new IllegalArgumentException("Coefficient must have exactly 6 decimal digits.");
        }
    }
}
