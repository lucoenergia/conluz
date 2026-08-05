package org.lucoenergia.conluz.infrastructure.production.sharingagreement.generatefile;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMissingRegulatoryCodeException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantService;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementCoefficientSumInvalidException;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileFormat;
import org.lucoenergia.conluz.domain.production.sharingagreement.generatefile.GenerateSharingAgreementFileService;
import org.lucoenergia.conluz.domain.production.sharingagreement.generatefile.GeneratedDistributorFile;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GenerateSharingAgreementFileServiceImpl implements GenerateSharingAgreementFileService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetPlantService getPlantService;

    public GenerateSharingAgreementFileServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                                    GetSupplyPartitionCoefficientRepository getCoefficientRepository,
                                                    GetSupplyRepository getSupplyRepository,
                                                    GetPlantService getPlantService) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.getCoefficientRepository = getCoefficientRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getPlantService = getPlantService;
    }

    @Override
    public GeneratedDistributorFile generate(UUID plantId, UUID sharingAgreementId, int year) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        if (!agreement.getPlantId().equals(plantId)) {
            throw new SharingAgreementNotFoundException(sharingAgreementId);
        }

        Plant plant = getPlantService.findById(PlantId.of(plantId));
        String regulatoryCode = plant.getRegulatoryCode();
        if (regulatoryCode == null || regulatoryCode.isBlank()) {
            throw new PlantMissingRegulatoryCodeException(plantId);
        }

        List<SupplyPartitionCoefficient> active = getCoefficientRepository.findAllBySharingAgreementId(sharingAgreementId)
                .stream()
                .filter(SupplyPartitionCoefficient::isActive)
                .toList();

        BigDecimal sum = active.stream()
                .map(SupplyPartitionCoefficient::getCoefficient)
                .map(DistributorFileFormat::normalizeScale)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!DistributorFileFormat.isValidSum(sum)) {
            throw new SharingAgreementCoefficientSumInvalidException(sharingAgreementId, sum);
        }

        Map<UUID, Supply> suppliesById = getSupplyRepository.findAllByIds(
                        active.stream().map(SupplyPartitionCoefficient::getSupplyId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Supply::getId, supply -> supply));

        String text = active.stream()
                .sorted(Comparator.comparing(c -> suppliesById.get(c.getSupplyId()).getCode()))
                .map(c -> DistributorFileFormat.formatCoefficientLine(suppliesById.get(c.getSupplyId()).getCode(), c.getCoefficient()))
                .collect(Collectors.joining(DistributorFileFormat.LINE_SEPARATOR, "", DistributorFileFormat.LINE_SEPARATOR));

        byte[] content = text.getBytes(DistributorFileFormat.CHARSET);
        String filename = DistributorFileFormat.buildFilename(regulatoryCode, year);

        return new GeneratedDistributorFile(filename, content);
    }
}
