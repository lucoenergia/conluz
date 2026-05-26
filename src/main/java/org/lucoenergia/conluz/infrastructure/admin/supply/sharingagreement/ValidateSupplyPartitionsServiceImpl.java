package org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement;


import org.lucoenergia.conluz.domain.admin.supply.create.ValidateSupplyPartitionsService;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.admin.supply.sharingagreement.create.CreateSupplyPartitionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;

@Transactional
@Service
public class ValidateSupplyPartitionsServiceImpl implements ValidateSupplyPartitionsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateSupplyPartitionsServiceImpl.class);

    private final MessageSource messageSource;

    public ValidateSupplyPartitionsServiceImpl(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
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
}
