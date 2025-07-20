package org.lucoenergia.conluz.infrastructure.admin.supply.create;


import org.lucoenergia.conluz.domain.admin.supply.*;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionService;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyPartitionRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.domain.shared.response.CreationInBulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Transactional
@Service
public class CreateSupplyPartitionServiceImpl implements CreateSupplyPartitionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateSupplyPartitionServiceImpl.class);

    private final CreateSupplyPartitionRepository repository;
    private final GetSupplyPartitionRepository getSupplyPartitionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final MessageSource messageSource;

    public CreateSupplyPartitionServiceImpl(CreateSupplyPartitionRepository repository,
                                            GetSupplyPartitionRepository getSupplyPartitionRepository,
                                            GetSupplyRepository getSupplyRepository,
                                            GetSharingAgreementRepository getSharingAgreementRepository,
                                            MessageSource messageSource) {
        this.repository = repository;
        this.getSupplyPartitionRepository = getSupplyPartitionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.messageSource = messageSource;
    }

    public void validateTotalCoefficient(Collection<CreateSupplyPartitionDto> suppliesPartitions) {
        double totalCoefficient = calculateTotalCoefficient(suppliesPartitions);
        if (Math.abs(totalCoefficient - 1.0) > 0.000001) {
            LOGGER.error("Sum of all coefficients must be equal to 1 but was {}.", totalCoefficient);
            String message = messageSource.getMessage("error.supply.partitions.invalid.total.coefficient",
                    Collections.emptyList().toArray(),
                    LocaleContextHolder.getLocale());
            throw new InvalidSupplyPartitionCoefficientException(message);
        }
    }

    private double calculateTotalCoefficient(Collection<CreateSupplyPartitionDto> suppliesPartitions) {
        return suppliesPartitions.stream()
                .mapToDouble(CreateSupplyPartitionDto::getCoefficient)
                .sum();
    }

    @Override
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

    private void validateCoefficient(Double coefficient) {
        if (coefficient <= 0 || coefficient >= 1) {
            LOGGER.error("Coefficient must be between 0 and 1 exclusively but was {}.", coefficient);
            String message = messageSource.getMessage("error.supply.partitions.invalid.coefficient.our.of.range",
                    Collections.emptyList().toArray(),
                    LocaleContextHolder.getLocale());
            throw new InvalidSupplyPartitionCoefficientException(message);
        }
        if (BigDecimal.valueOf(coefficient).scale() != 6) {
            LOGGER.error("Coefficient must have exactly 6 decimal digits but was {}.", coefficient);
            String message = messageSource.getMessage("error.supply.partitions.invalid.coefficient.decimals",
                    Collections.emptyList().toArray(),
                    LocaleContextHolder.getLocale());
            throw new InvalidSupplyPartitionCoefficientException(message);
        }
    }

    @Override
    public CreationInBulkResponse<CreateSupplyPartitionDto, SupplyPartition> createInBulk(
            List<CreateSupplyPartitionDto> suppliesPartitions, SharingAgreementId sharingAgreementId) {

        CreationInBulkResponse<CreateSupplyPartitionDto, SupplyPartition> response = new CreationInBulkResponse<>();

        if (suppliesPartitions == null || suppliesPartitions.isEmpty()) {
            return response;
        }

        validateTotalCoefficient(suppliesPartitions);

        // Verify if sharing agreement exists
        Optional<SharingAgreement> agreement = getSharingAgreementRepository.findById(sharingAgreementId);
        if (agreement.isEmpty()) {
            throw new SharingAgreementNotFoundException(sharingAgreementId);
        }

        suppliesPartitions.forEach(supplyPartition -> {
            try {
                SupplyPartition newSupplyPartition = create(SupplyCode.of(supplyPartition.getCode()),
                        supplyPartition.getCoefficient(),
                        sharingAgreementId
                );
                response.addCreated(newSupplyPartition);
            } catch (SupplyAlreadyExistsException e) {
                LOGGER.error("Supply with code {} already exists", supplyPartition.getCode(), e);
                response.addError(supplyPartition,
                        messageSource.getMessage("error.supply.already.exists",
                                Collections.singletonList(supplyPartition.getCode()).toArray(),
                                LocaleContextHolder.getLocale()));
            } catch (Exception e) {
                LOGGER.error("Unable to create supply partition", e);
                response.addError(supplyPartition,
                        messageSource.getMessage("error.supply.unable.to.create", new List[]{},
                                LocaleContextHolder.getLocale()));
            }
        });

        return response;
    }
}
