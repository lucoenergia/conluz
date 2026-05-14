package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.SharingAgreement;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSharingAgreementWithPartitionsService;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyPartitionService;
import org.lucoenergia.conluz.infrastructure.admin.supply.InvalidSupplyPartitionCoefficientException;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.io.CsvFileParser;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CreateSharingAgreementWithPartitionsServiceImpl implements CreateSharingAgreementWithPartitionsService {

    private final SharingAgreementRepository sharingAgreementRepository;
    private final SharingAgreementEntityMapper sharingAgreementEntityMapper;
    private final SupplyRepository supplyRepository;
    private final SupplyPartitionRepository supplyPartitionRepository;
    private final CreateSupplyPartitionService createSupplyPartitionService;
    private final CsvFileParser csvFileParser;
    private final MessageSource messageSource;

    public CreateSharingAgreementWithPartitionsServiceImpl(
            SharingAgreementRepository sharingAgreementRepository,
            SharingAgreementEntityMapper sharingAgreementEntityMapper,
            SupplyRepository supplyRepository,
            SupplyPartitionRepository supplyPartitionRepository,
            CreateSupplyPartitionService createSupplyPartitionService,
            CsvFileParser csvFileParser,
            MessageSource messageSource) {
        this.sharingAgreementRepository = sharingAgreementRepository;
        this.sharingAgreementEntityMapper = sharingAgreementEntityMapper;
        this.supplyRepository = supplyRepository;
        this.supplyPartitionRepository = supplyPartitionRepository;
        this.createSupplyPartitionService = createSupplyPartitionService;
        this.csvFileParser = csvFileParser;
        this.messageSource = messageSource;
    }

    @Override
    public SharingAgreement create(LocalDate startDate, LocalDate endDate, String notes, MultipartFile file) {
        List<CreateSupplyPartitionDto> rows;
        try {
            rows = csvFileParser.parse(file.getInputStream(), CreateSupplyPartitionDto.class, ';');
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse file: " + e.getMessage(), e);
        }

        validateEachCoefficient(rows);
        createSupplyPartitionService.validateTotalCoefficient(rows);

        Optional<SharingAgreementEntity> activeAgreement = sharingAgreementRepository.findFirstByEndDateIsNull();
        activeAgreement.ifPresent(active -> {
            active.setEndDate(startDate.minusDays(1));
            sharingAgreementRepository.save(active);
        });

        SharingAgreementEntity newEntity = new SharingAgreementEntity();
        newEntity.setId(UUID.randomUUID());
        newEntity.setStartDate(startDate);
        newEntity.setEndDate(endDate);
        newEntity.setNotes(notes);
        SharingAgreementEntity savedEntity = sharingAgreementRepository.save(newEntity);

        for (CreateSupplyPartitionDto row : rows) {
            Optional<SupplyEntity> supply = supplyRepository.findByCode(row.getCode());
            if (supply.isEmpty()) {
                continue;
            }

            SupplyPartitionEntity partition = new SupplyPartitionEntity();
            partition.setId(UUID.randomUUID());
            partition.setSupply(supply.get());
            partition.setSharingAgreement(savedEntity);
            partition.setCoefficient(row.getCoefficient());
            supplyPartitionRepository.save(partition);

            SupplyEntity supplyEntity = supply.get();
            supplyEntity.setPartitionCoefficient(row.getCoefficient().floatValue());
            supplyRepository.save(supplyEntity);
        }

        return sharingAgreementEntityMapper.map(savedEntity);
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
