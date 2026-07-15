package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileError;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileErrorCode;
import org.lucoenergia.conluz.domain.production.plant.distributorfile.DistributorFileValidationException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.DistributorFileStoreResult;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.DownloadSharingAgreementFileService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.SharingAgreementMismatchException;
import org.lucoenergia.conluz.domain.production.plant.sharingagreementfile.StoreDistributorFileService;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.ContentHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class StoreDistributorFileServiceImplIntegrationTest extends BaseIntegrationTest {

    private static final String REGULATORY_CODE = "ES0031300325733001FH0FA000";
    private static final String FILENAME = REGULATORY_CODE + "_2023.txt";
    private static final String CUPS_1 = "ES0031300325733001FH0F";
    private static final String CUPS_2 = "ES0031300325733002FH0F";

    @Autowired
    private StoreDistributorFileService storeDistributorFileService;

    @Autowired
    private DownloadSharingAgreementFileService downloadSharingAgreementFileService;

    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SupplyRepository supplyRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private UserEntity persistUser() {
        return userRepository.save(UserMother.randomUserEntity());
    }

    private SupplyEntity persistSupply(UserEntity user, CommunityEntity community, String cups) {
        SupplyEntity supply = SupplyEntityMother.random(user, community);
        supply.setCode(cups);
        return supplyRepository.save(supply);
    }

    private PlantEntity persistPlant(SupplyEntity supply, String regulatoryCode) {
        return plantRepository.save(PlantMother.randomPlantEntity()
                .withSupply(supply)
                .withRegulatoryCode(regulatoryCode)
                .build());
    }

    private SharingAgreementEntity persistPublishedAgreement(PlantEntity plant) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.PUBLISHED);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private byte[] content(String... lines) {
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void crossCommunityCupsIsUnknownToThePlantsCommunity() {
        CommunityEntity communityA = persistCommunity();
        CommunityEntity communityB = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supplyA1 = persistSupply(user, communityA, CUPS_1);
        SupplyEntity supplyB1 = persistSupply(user, communityB, CUPS_2);
        PlantEntity plantA = persistPlant(supplyA1, REGULATORY_CODE);
        SharingAgreementEntity agreementA = persistPublishedAgreement(plantA);

        byte[] file = content(CUPS_1 + ";0,500000", CUPS_2 + ";0,500000");

        DistributorFileValidationException exception = assertThrows(DistributorFileValidationException.class,
                () -> storeDistributorFileService.store(plantA.getId(), agreementA.getId(), FILENAME, file,
                        user.getId()));

        long unknownCupsErrors = exception.getErrors().stream()
                .filter(e -> e.getCode() == DistributorFileErrorCode.CUPS_UNKNOWN)
                .count();
        assertEquals(1, unknownCupsErrors);
        DistributorFileError error = exception.getErrors().stream()
                .filter(e -> e.getCode() == DistributorFileErrorCode.CUPS_UNKNOWN)
                .findFirst().orElseThrow();
        assertEquals(CUPS_2, error.getParams().get("cups"));
        assertEquals("2", error.getParams().get("line"));

        // supplyB1's own community membership is irrelevant here -- kept only so the entity is used
        // meaningfully rather than as dead setup.
        assertNotEquals(supplyA1.getCommunity().getId(), supplyB1.getCommunity().getId());
    }

    @Test
    void storeAndDownloadRoundTripIsByteIdentical() {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply1 = persistSupply(user, community, CUPS_1);
        persistSupply(user, community, CUPS_2);
        PlantEntity plant = persistPlant(supply1, REGULATORY_CODE);
        SharingAgreementEntity agreement = persistPublishedAgreement(plant);

        byte[] original = content(CUPS_1 + ";0,500000", CUPS_2 + ";0,500000");

        DistributorFileStoreResult result = storeDistributorFileService.store(
                plant.getId(), agreement.getId(), FILENAME, original, user.getId());

        assertEquals(2, result.getEntries().size());

        SharingAgreementFile byId = downloadSharingAgreementFileService.downloadById(result.getFile().getId());
        SharingAgreementFile latest = downloadSharingAgreementFileService
                .downloadLatestBySharingAgreementId(agreement.getId());

        assertTrue(Arrays.equals(original, byId.getContent()));
        assertTrue(Arrays.equals(original, latest.getContent()));
        assertEquals(ContentHasher.sha256Hex(original), byId.getContentHash());
        assertEquals(ContentHasher.sha256Hex(original), latest.getContentHash());
    }

    @Test
    void secondUploadOfIdenticalBytesProducesNewRowWithSameHash() {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supply1 = persistSupply(user, community, CUPS_1);
        persistSupply(user, community, CUPS_2);
        PlantEntity plant = persistPlant(supply1, REGULATORY_CODE);
        SharingAgreementEntity agreement = persistPublishedAgreement(plant);

        byte[] original = content(CUPS_1 + ";0,500000", CUPS_2 + ";0,500000");

        DistributorFileStoreResult first = storeDistributorFileService.store(
                plant.getId(), agreement.getId(), FILENAME, original, user.getId());
        DistributorFileStoreResult second = storeDistributorFileService.store(
                plant.getId(), agreement.getId(), FILENAME, original, user.getId());

        assertNotEquals(first.getFile().getId(), second.getFile().getId());
        assertEquals(first.getFile().getContentHash(), second.getFile().getContentHash());
    }

    @Test
    void mismatchedSharingAgreementIdThrowsMismatchException() {
        CommunityEntity community = persistCommunity();
        UserEntity user = persistUser();
        SupplyEntity supplyA1 = persistSupply(user, community, CUPS_1);
        persistSupply(user, community, CUPS_2);
        PlantEntity plantA = persistPlant(supplyA1, REGULATORY_CODE);
        persistPublishedAgreement(plantA);

        // A second plant/agreement in a different community -- its agreement id does not belong to plantA.
        CommunityEntity communityB = persistCommunity();
        SupplyEntity supplyB1 = persistSupply(user, communityB, "ES0031300325733009FH0F");
        PlantEntity plantB = persistPlant(supplyB1, "ES0031300325733009FH0FA000");
        SharingAgreementEntity agreementB = persistPublishedAgreement(plantB);

        byte[] file = content(CUPS_1 + ";0,500000", CUPS_2 + ";0,500000");

        assertThrows(SharingAgreementMismatchException.class,
                () -> storeDistributorFileService.store(plantA.getId(), agreementB.getId(), FILENAME, file,
                        user.getId()));
    }
}
