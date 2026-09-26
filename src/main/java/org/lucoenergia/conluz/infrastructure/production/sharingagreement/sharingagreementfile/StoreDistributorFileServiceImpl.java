package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileFormat;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileParseResult;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileParser;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileValidationException;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.SupplyCupsCollisionException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.sharingagreement.ResolvedCoefficientEntry;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.DistributorFileStoreResult;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SaveSharingAgreementFileRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.StoreDistributorFileService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.shared.ContentHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates a distributor coefficient-partition file against a plant, stores it as evidence on an
 * existing DRAFT sharing agreement, and materialises its parsed entries as pending coefficient
 * rows -- all in one transaction, so a materialisation failure rolls back the file save too.
 */
@Transactional
@Service
public class StoreDistributorFileServiceImpl implements StoreDistributorFileService {

    private final GetPlantRepository getPlantRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final GetSharingAgreementService getSharingAgreementService;
    private final DistributorFileParser parser;
    private final SaveSharingAgreementFileRepository saveSharingAgreementFileRepository;
    private final MaterializeSharingAgreementCoefficientsService materializeSharingAgreementCoefficientsService;

    public StoreDistributorFileServiceImpl(GetPlantRepository getPlantRepository,
                                            GetSupplyRepository getSupplyRepository,
                                            GetSharingAgreementService getSharingAgreementService,
                                            DistributorFileParser parser,
                                            SaveSharingAgreementFileRepository saveSharingAgreementFileRepository,
                                            MaterializeSharingAgreementCoefficientsService materializeSharingAgreementCoefficientsService) {
        this.getPlantRepository = getPlantRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.getSharingAgreementService = getSharingAgreementService;
        this.parser = parser;
        this.saveSharingAgreementFileRepository = saveSharingAgreementFileRepository;
        this.materializeSharingAgreementCoefficientsService = materializeSharingAgreementCoefficientsService;
    }

    @Override
    public DistributorFileStoreResult store(UUID plantId, UUID sharingAgreementId, String filename, byte[] content,
                                             UUID uploadedBy) {
        SharingAgreement agreement = getSharingAgreementService.findById(sharingAgreementId);
        agreement.assertDraft();

        Plant plant = getPlantRepository.findById(PlantId.of(plantId))
                .orElseThrow(() -> new PlantNotFoundException(PlantId.of(plantId)));

        UUID communityId = plant.getSupply().getCommunity().getId();
        Map<String, Supply> suppliesByCups = indexByNormalizedCups(getSupplyRepository.findAllByCommunityId(communityId));

        DistributorFileParseResult result = parser.parse(filename, content, plant.getRegulatoryCode(),
                suppliesByCups.keySet());
        if (!result.isValid()) {
            throw new DistributorFileValidationException(result.getErrors());
        }

        SharingAgreementFile file = new SharingAgreementFile.Builder()
                .withId(UUID.randomUUID())
                .withSharingAgreementId(sharingAgreementId)
                .withFilename(filename)
                .withContent(content)
                .withContentHash(ContentHasher.sha256Hex(content))
                .withUploadedAt(Instant.now())
                .withUploadedBy(uploadedBy)
                .build();

        SharingAgreementFile saved = saveSharingAgreementFileRepository.save(file, plantId);

        // Resolved here rather than re-looked-up by CUPS downstream: the supplies are already in
        // hand, and the file's CUPS is the normalized form, which need not match the stored code.
        List<ResolvedCoefficientEntry> entries = result.getEntries().stream()
                .map(entry -> new ResolvedCoefficientEntry(suppliesByCups.get(entry.getCups()).getId(),
                        entry.getCoefficient()))
                .collect(Collectors.toList());
        materializeSharingAgreementCoefficientsService.replaceAllBySupplyId(plantId, sharingAgreementId, entries);

        return new DistributorFileStoreResult(saved, result.getEntries());
    }

    /**
     * Indexes a community's supplies by the CUPS a distributor file would carry for them, so a file
     * written with the 22-character form resolves against a supply stored with the 20-character one.
     *
     * <p>A supply whose stored code cannot be normalized is skipped rather than failing the upload:
     * one unusable supply must not block a community from importing a file that does not reference
     * it, and a file line that does reference it still fails as an unknown CUPS.
     *
     * <p>A collision is different -- two stored codes normalizing to one CUPS makes that CUPS
     * ambiguous, so there is no correct supply to resolve it to.
     */
    private Map<String, Supply> indexByNormalizedCups(List<Supply> supplies) {
        Map<String, List<Supply>> byCups = new TreeMap<>();
        for (Supply supply : supplies) {
            Optional<String> cups = DistributorFileFormat.normalizeCups(supply.getCode());
            cups.ifPresent(value -> byCups.computeIfAbsent(value, key -> new ArrayList<>()).add(supply));
        }

        Map<String, Supply> suppliesByCups = new HashMap<>();
        for (Map.Entry<String, List<Supply>> entry : byCups.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> codes = entry.getValue().stream().map(Supply::getCode).collect(Collectors.toList());
                Collections.sort(codes);
                throw new SupplyCupsCollisionException(entry.getKey(), codes);
            }
            suppliesByCups.put(entry.getKey(), entry.getValue().get(0));
        }
        return suppliesByCups;
    }
}
