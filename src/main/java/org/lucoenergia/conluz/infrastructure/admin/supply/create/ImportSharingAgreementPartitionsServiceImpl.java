package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementId;
import org.lucoenergia.conluz.domain.admin.supply.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionService;
import org.lucoenergia.conluz.domain.admin.supply.create.ImportSharingAgreementPartitionsService;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ImportSharingAgreementPartitionsServiceImpl implements ImportSharingAgreementPartitionsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportSharingAgreementPartitionsServiceImpl.class);

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SupplyRepository supplyRepository;
    private final SupplyPartitionRepository supplyPartitionRepository;
    private final CreateSupplyPartitionService createSupplyPartitionService;
    private final CsvFileParser csvFileParser;
    private final MessageSource messageSource;

    public ImportSharingAgreementPartitionsServiceImpl(
            SharingAgreementRepository sharingAgreementRepository,
            SupplyRepository supplyRepository,
            SupplyPartitionRepository supplyPartitionRepository,
            CreateSupplyPartitionService createSupplyPartitionService,
            CsvFileParser csvFileParser,
            MessageSource messageSource) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.supplyRepository = supplyRepository;
        this.supplyPartitionRepository = supplyPartitionRepository;
        this.createSupplyPartitionService = createSupplyPartitionService;
        this.csvFileParser = csvFileParser;
        this.messageSource = messageSource;
    }

    @Override
    public ImportSharingAgreementPartitionsResponse importPartitions(SharingAgreementId id, MultipartFile file) {
        sharingAgreementRepository.findById(id.getId())
                .orElseThrow(() -> new SharingAgreementNotFoundException(id));

        List<CreateSupplyPartitionDto> rows;
        try {
            rows = csvFileParser.parse(file.getInputStream(), CreateSupplyPartitionDto.class, ';');
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse file: " + e.getMessage(), e);
        }

        validateEachCoefficient(rows);
        createSupplyPartitionService.validateTotalCoefficient(rows);

        ImportSharingAgreementPartitionsResponse response = new ImportSharingAgreementPartitionsResponse();

        for (CreateSupplyPartitionDto row : rows) {
            Optional<SupplyEntity> supply = supplyRepository.findByCode(row.getCode());
            if (supply.isEmpty()) {
                String message = messageSource.getMessage(
                        "error.supply.not.found.by.code",
                        new Object[]{row.getCode()},
                        LocaleContextHolder.getLocale());
                response.addError(row.getCode(), message);
                continue;
            }

            try {
                UUID supplyId = supply.get().getId();
                Optional<SupplyPartitionEntity> existing =
                        supplyPartitionRepository.findBySupplyIdAndSharingAgreementId(supplyId, id.getId());

                if (existing.isPresent()) {
                    existing.get().setCoefficient(row.getCoefficient());
                    supplyPartitionRepository.save(existing.get());
                } else {
                    SupplyPartitionEntity partition = new SupplyPartitionEntity();
                    partition.setId(UUID.randomUUID());
                    partition.setSupply(supply.get());
                    partition.setSharingAgreement(sharingAgreementRepository.findById(id.getId()).get());
                    partition.setCoefficient(row.getCoefficient());
                    supplyPartitionRepository.save(partition);
                }

                SupplyEntity supplyEntity = supply.get();
                supplyEntity.setPartitionCoefficient(row.getCoefficient().floatValue());
                supplyRepository.save(supplyEntity);

                response.addCreated(row.getCode());
            } catch (Exception e) {
                LOGGER.error("Error importing partition for CUPS {}", row.getCode(), e);
                response.addError(row.getCode(), e.getMessage());
            }
        }

        return response;
    }

    private void validateEachCoefficient(List<CreateSupplyPartitionDto> rows) {
        for (CreateSupplyPartitionDto row : rows) {
            Double coefficient = row.getCoefficient();
            if (coefficient == null || coefficient <= 0 || coefficient > 1) {
                String message = messageSource.getMessage(
                        "error.supply.partitions.invalid.coefficient.our.of.range",
                        Collections.emptyList().toArray(),
                        LocaleContextHolder.getLocale());
                throw new InvalidSupplyPartitionCoefficientException(message);
            }
        }
    }
}
