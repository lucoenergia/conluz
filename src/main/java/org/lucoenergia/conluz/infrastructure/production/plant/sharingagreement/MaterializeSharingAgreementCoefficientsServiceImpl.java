package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.DuplicatePartitionCoefficientEntryException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.PendingCoefficientEntry;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementMismatchException;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
@Service
public class MaterializeSharingAgreementCoefficientsServiceImpl implements MaterializeSharingAgreementCoefficientsService {

    private final GetSharingAgreementService getSharingAgreementService;
    private final GetPlantRepository getPlantRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final SaveSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;

    public MaterializeSharingAgreementCoefficientsServiceImpl(GetSharingAgreementService getSharingAgreementService,
                                                                GetPlantRepository getPlantRepository,
                                                                GetSupplyRepository getSupplyRepository,
                                                                SaveSupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository) {
        this.getSharingAgreementService = getSharingAgreementService;
        this.getPlantRepository = getPlantRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.supplyPartitionCoefficientRepository = supplyPartitionCoefficientRepository;
    }

    @Override
    public List<SupplyPartitionCoefficient> replaceAll(UUID plantId, UUID sharingAgreementId,
                                                         List<PendingCoefficientEntry> entries) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        if (!agreement.getPlantId().equals(plantId)) {
            throw new SharingAgreementMismatchException(sharingAgreementId, plantId);
        }
        agreement.assertDraft();

        assertNoDuplicateCups(sharingAgreementId, entries);

        Plant plant = getPlantRepository.findById(PlantId.of(plantId))
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(plantId)));
        UUID communityId = plant.getSupply().getCommunity().getId();

        List<SupplyPartitionCoefficient> pendingRows = entries.stream()
                .map(entry -> toPendingRow(entry, plantId, sharingAgreementId, communityId))
                .collect(Collectors.toList());

        return supplyPartitionCoefficientRepository.replaceAllForSharingAgreement(sharingAgreementId, pendingRows);
    }

    private void assertNoDuplicateCups(UUID sharingAgreementId, List<PendingCoefficientEntry> entries) {
        Set<String> seen = new HashSet<>();
        for (PendingCoefficientEntry entry : entries) {
            if (!seen.add(entry.getCups())) {
                throw new DuplicatePartitionCoefficientEntryException(sharingAgreementId, entry.getCups());
            }
        }
    }

    private SupplyPartitionCoefficient toPendingRow(PendingCoefficientEntry entry, UUID plantId,
                                                      UUID sharingAgreementId, UUID communityId) {
        Supply supply = getSupplyRepository.findByCode(SupplyCode.of(entry.getCups()))
                .filter(candidate -> candidate.getCommunity().getId().equals(communityId))
                .orElseThrow(() -> new SupplyNotFoundException(SupplyCode.of(entry.getCups())));

        return new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plantId)
                .withSharingAgreementId(sharingAgreementId)
                .withCoefficient(entry.getCoefficient())
                .withValidFrom(null)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build();
    }
}
