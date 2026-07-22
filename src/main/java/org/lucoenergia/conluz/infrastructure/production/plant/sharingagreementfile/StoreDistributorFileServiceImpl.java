package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileParseResult;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileParser;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileValidationException;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetSharingAgreementService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.PendingCoefficientEntry;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.DistributorFileStoreResult;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SaveSharingAgreementFileRepository;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.StoreDistributorFileService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.shared.ContentHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
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
        Set<String> knownCups = getSupplyRepository.findAllByCommunityId(communityId).stream()
                .map(Supply::getCode)
                .collect(Collectors.toSet());

        DistributorFileParseResult result = parser.parse(filename, content, plant.getRegulatoryCode(), knownCups);
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

        List<PendingCoefficientEntry> entries = result.getEntries().stream()
                .map(entry -> new PendingCoefficientEntry(entry.getCups(), entry.getCoefficient()))
                .collect(Collectors.toList());
        materializeSharingAgreementCoefficientsService.replaceAll(plantId, sharingAgreementId, entries);

        return new DistributorFileStoreResult(saved, result.getEntries());
    }
}
