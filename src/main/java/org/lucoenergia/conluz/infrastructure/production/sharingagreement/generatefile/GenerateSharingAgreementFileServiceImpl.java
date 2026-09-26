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
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.SupplyCupsCollisionException;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.SupplyCupsNotNormalizableException;
import org.lucoenergia.conluz.domain.production.sharingagreement.generatefile.GenerateSharingAgreementFileService;
import org.lucoenergia.conluz.domain.production.sharingagreement.generatefile.GeneratedDistributorFile;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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

        // The agreement's coefficient set is complete and immutable -- one row per supply,
        // regardless of status. validFrom/validTo are application-lifecycle metadata (when the
        // distributor started applying a coefficient, when it was superseded); they say nothing
        // about whether a supply's share belongs in the file. The distributor file is the full
        // reparto, so every row is exported as-is.
        List<SupplyPartitionCoefficient> coefficients = getCoefficientRepository.findAllBySharingAgreementId(sharingAgreementId);

        BigDecimal sum = DistributorFileFormat.normalizedSum(
                coefficients.stream().map(SupplyPartitionCoefficient::getCoefficient).toList());
        if (!DistributorFileFormat.isValidSum(sum)) {
            throw new SharingAgreementCoefficientSumInvalidException(sharingAgreementId, sum);
        }

        Map<UUID, Supply> suppliesById = getSupplyRepository.findAllByIds(
                        coefficients.stream().map(SupplyPartitionCoefficient::getSupplyId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Supply::getId, supply -> supply));

        Map<UUID, String> cupsBySupplyId = normalizeCups(suppliesById);

        String text = coefficients.stream()
                .sorted(Comparator.comparing(c -> cupsBySupplyId.get(c.getSupplyId())))
                .map(c -> DistributorFileFormat.formatCoefficientLine(cupsBySupplyId.get(c.getSupplyId()), c.getCoefficient()))
                // No trailing separator: the specification counts a line break after the last CUPS as
                // an extra, malformed line.
                .collect(Collectors.joining(DistributorFileFormat.LINE_SEPARATOR));

        byte[] content = text.getBytes(DistributorFileFormat.CHARSET);
        String filename = DistributorFileFormat.buildFilename(regulatoryCode, year);

        return new GeneratedDistributorFile(filename, content);
    }

    /**
     * Maps each supply to the CUPS that will be written for it, rejecting the two data shapes that
     * cannot produce a valid file. Every unnormalizable code is collected before throwing, so a
     * community fixing its supplies sees the full list at once rather than one code per attempt.
     * A collision -- two stored codes normalizing to the same CUPS -- would put the same CUPS on two
     * lines, which the distributor rejects.
     */
    private Map<UUID, String> normalizeCups(Map<UUID, Supply> suppliesById) {
        Map<UUID, String> cupsBySupplyId = new HashMap<>();
        // Sorted so that the codes reported in an error are stable across runs: iteration order of
        // the supplies is not.
        Map<String, List<String>> codesByCups = new TreeMap<>();
        List<String> notNormalizable = new ArrayList<>();

        for (Supply supply : suppliesById.values()) {
            Optional<String> cups = DistributorFileFormat.normalizeCups(supply.getCode());
            if (cups.isEmpty()) {
                notNormalizable.add(supply.getCode());
                continue;
            }
            cupsBySupplyId.put(supply.getId(), cups.get());
            codesByCups.computeIfAbsent(cups.get(), key -> new ArrayList<>()).add(supply.getCode());
        }

        if (!notNormalizable.isEmpty()) {
            Collections.sort(notNormalizable);
            throw new SupplyCupsNotNormalizableException(notNormalizable);
        }
        for (Map.Entry<String, List<String>> entry : codesByCups.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> collidingCodes = new ArrayList<>(entry.getValue());
                Collections.sort(collidingCodes);
                throw new SupplyCupsCollisionException(entry.getKey(), collidingCodes);
            }
        }

        return cupsBySupplyId;
    }
}
