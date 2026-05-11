package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PartitionCoefficientService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.RegisterPartitionCoefficientInBulkService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Transactional
@Service
public class RegisterPartitionCoefficientInBulkServiceImpl implements RegisterPartitionCoefficientInBulkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterPartitionCoefficientInBulkServiceImpl.class);

    private final GetSupplyRepository getSupplyRepository;
    private final PartitionCoefficientService partitionCoefficientService;
    private final MessageSource messageSource;

    public RegisterPartitionCoefficientInBulkServiceImpl(
            GetSupplyRepository getSupplyRepository,
            PartitionCoefficientService partitionCoefficientService,
            MessageSource messageSource) {
        this.getSupplyRepository = getSupplyRepository;
        this.partitionCoefficientService = partitionCoefficientService;
        this.messageSource = messageSource;
    }

    @Override
    public RegisterPartitionCoefficientsWithFileResponse registerInBulk(
            List<RegisterPartitionCoefficientFileRow> rows, Instant effectiveAt) {

        RegisterPartitionCoefficientsWithFileResponse response = new RegisterPartitionCoefficientsWithFileResponse();

        for (RegisterPartitionCoefficientFileRow row : rows) {
            Optional<Supply> supply = getSupplyRepository.findByCode(SupplyCode.of(row.getCups()));
            if (supply.isEmpty()) {
                String message = messageSource.getMessage(
                        "error.supply.not.found.by.code",
                        new Object[]{row.getCups()},
                        LocaleContextHolder.getLocale());
                response.addError(row.getCups(), message);
                continue;
            }
            try {
                SupplyPartitionCoefficient saved = partitionCoefficientService.registerCoefficientChange(
                        supply.get().getId(), row.getCoefficient(), effectiveAt);
                response.addCreated(new PartitionCoefficientResponse(saved));
            } catch (Exception ex) {
                LOGGER.error("Error registering coefficient for CUPS {}", row.getCups(), ex);
                response.addError(row.getCups(), ex.getMessage());
            }
        }

        BigDecimal communitySum = partitionCoefficientService.computeCommunitySum(effectiveAt);
        response.applyCommunitySum(communitySum);

        return response;
    }
}
