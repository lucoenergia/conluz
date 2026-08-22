package org.lucoenergia.conluz.infrastructure.production.sharingagreement;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.DuplicatePartitionCoefficientEntryException;
import org.lucoenergia.conluz.domain.production.sharingagreement.DuplicatePartitionCoefficientSupplyException;
import org.lucoenergia.conluz.domain.production.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.sharingagreement.PendingCoefficientEntry;
import org.lucoenergia.conluz.domain.production.sharingagreement.ResolvedCoefficientEntry;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementPlantMismatchException;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
        return replaceAllInternal(plantId, sharingAgreementId,
                () -> assertNoDuplicateCups(sharingAgreementId, entries),
                communityId -> entries.stream()
                        .map(entry -> toPendingRow(entry, plantId, sharingAgreementId, communityId))
                        .collect(Collectors.toList()));
    }

    @Override
    public List<SupplyPartitionCoefficient> replaceAllBySupplyId(UUID plantId, UUID sharingAgreementId,
                                                                 List<ResolvedCoefficientEntry> entries) {
        return replaceAllInternal(plantId, sharingAgreementId,
                () -> assertNoDuplicateSupplyId(sharingAgreementId, entries),
                communityId -> entries.stream()
                        .map(entry -> toPendingRowFromSupplyId(entry, plantId, sharingAgreementId, communityId))
                        .collect(Collectors.toList()));
    }

    /**
     * Shared replace sequence: verifies {@code sharingAgreementId} belongs to {@code plantId}, asserts
     * DRAFT, runs the caller's duplicate check, resolves the plant's community, builds the pending
     * rows via the caller's resolver, then atomically replaces the agreement's coefficient set. Used
     * by both the CUPS-keyed {@link #replaceAll} (distributor-file import + legacy manual path) and
     * the supplyId-keyed replace method.
     */
    private List<SupplyPartitionCoefficient> replaceAllInternal(UUID plantId, UUID sharingAgreementId,
                                                                  Runnable duplicateCheck,
                                                                  Function<UUID, List<SupplyPartitionCoefficient>> rowBuilder) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        if (!agreement.getPlantId().equals(plantId)) {
            throw new SharingAgreementPlantMismatchException(sharingAgreementId, plantId);
        }
        agreement.assertDraft();

        duplicateCheck.run();

        Plant plant = getPlantRepository.findById(PlantId.of(plantId))
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(plantId)));
        UUID communityId = plant.getSupply().getCommunity().getId();

        List<SupplyPartitionCoefficient> pendingRows = rowBuilder.apply(communityId);

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

    private void assertNoDuplicateSupplyId(UUID sharingAgreementId, List<ResolvedCoefficientEntry> entries) {
        Set<UUID> seen = new HashSet<>();
        for (ResolvedCoefficientEntry entry : entries) {
            if (!seen.add(entry.getSupplyId())) {
                throw new DuplicatePartitionCoefficientSupplyException(sharingAgreementId, entry.getSupplyId());
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

    private SupplyPartitionCoefficient toPendingRowFromSupplyId(ResolvedCoefficientEntry entry, UUID plantId,
                                                                  UUID sharingAgreementId, UUID communityId) {
        Supply supply = getSupplyRepository.findById(SupplyId.of(entry.getSupplyId()))
                .filter(candidate -> candidate.getCommunity().getId().equals(communityId))
                .orElseThrow(() -> new SupplyNotFoundException(SupplyId.of(entry.getSupplyId())));

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
