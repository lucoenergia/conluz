package org.lucoenergia.conluz.infrastructure.production.plant.sharingagreementfile;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreementStatus;
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
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Isolated from StoreDistributorFileServiceImplIntegrationTest because it needs the whole
 * MaterializeSharingAgreementCoefficientsService bean replaced (to force a failure after the file
 * has already been persisted), which would break that class's other, real-materialisation tests if
 * they shared a test class.
 */
class StoreDistributorFileServiceImplTransactionRollbackTest extends BaseIntegrationTest {

    // This test class does not roll back (see class javadoc), so its writes persist in the shared
    // Testcontainers database for the rest of the test run -- these constants must not collide with
    // any other test class's hardcoded regulatory code / CUPS.
    private static final String REGULATORY_CODE = "ES0031300999999999FH0FA000";
    private static final String FILENAME = REGULATORY_CODE + "_2023.txt";
    private static final String CUPS_1 = "ES0031300999999998FH0F";

    @Autowired
    private StoreDistributorFileService storeDistributorFileService;
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
    @Autowired
    private SharingAgreementFileRepository sharingAgreementFileRepository;
    @MockitoBean
    private MaterializeSharingAgreementCoefficientsService materializeSharingAgreementCoefficientsService;

    @Test
    void materializationFailureAfterFileStorageRollsBackTheWholeTransaction() {
        CommunityEntity community = communityJpaRepository.save(CommunityMother.randomEntity().build());
        UserEntity user = userRepository.save(UserMother.randomUserEntity());
        SupplyEntity supply = SupplyEntityMother.random(user, community);
        supply.setCode(CUPS_1);
        supply = supplyRepository.save(supply);
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity()
                .withSupply(supply)
                .withRegulatoryCode(REGULATORY_CODE)
                .build());
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.DRAFT);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        agreement = sharingAgreementRepository.save(agreement);

        when(materializeSharingAgreementCoefficientsService.replaceAll(any(), any(), any()))
                .thenThrow(new RuntimeException("Injected materialisation failure for test"));

        byte[] file = (CUPS_1 + ";1,000000\n").getBytes(StandardCharsets.UTF_8);

        UUID plantId = plant.getId();
        UUID agreementId = agreement.getId();
        assertThrows(RuntimeException.class,
                () -> storeDistributorFileService.store(plantId, agreementId, FILENAME, file, user.getId()));

        List<SharingAgreementFileEntity> files = sharingAgreementFileRepository.findAll().stream()
                        .filter(f -> f.getSharingAgreement().getId().equals(agreementId))
                        .toList();
        assertTrue(files.isEmpty(), "No SharingAgreementFile row must be committed when materialisation fails");
    }
}
