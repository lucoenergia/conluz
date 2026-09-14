package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.get.GetSharingAgreementRepositoryDatabase;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile.SharingAgreementFileRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.ContentHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class GetSharingAgreementRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSharingAgreementRepositoryDatabase repository;
    @Autowired
    private SharingAgreementRepository sharingAgreementJpaRepository;
    @Autowired
    private SharingAgreementEntityMapper sharingAgreementEntityMapper;
    @Autowired
    private SharingAgreementFileRepository sharingAgreementFileRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private EntityManager entityManager;

    private PlantEntity persistPlant() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        SupplyEntity supply = supplyRepository.save(SupplyEntityMother.random(
                user, communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)));
        return plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
    }

    private SharingAgreementEntity persistAgreement(PlantEntity plant, SharingAgreementStatus status, Instant createdAt) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(createdAt);
        agreement.setCreatedBy(null);
        return sharingAgreementJpaRepository.save(agreement);
    }

    private SharingAgreementFileEntity persistFile(SharingAgreementEntity agreement, UUID id, String filename, Instant uploadedAt) {
        UserEntity uploader = UserMother.randomUserEntity();
        userRepository.save(uploader);
        byte[] content = ("content-" + id).getBytes(StandardCharsets.UTF_8);

        SharingAgreementFileEntity file = new SharingAgreementFileEntity();
        file.setId(id);
        file.setSharingAgreement(agreement);
        file.setFilename(filename);
        file.setContent(content);
        file.setContentHash(ContentHasher.sha256Hex(content));
        // Postgres TIMESTAMPTZ only stores microsecond precision; truncate here so the persisted
        // value read back later compares equal to what the test asserts, rather than being off by
        // the nanosecond remainder Instant.now() carries.
        file.setUploadedAt(uploadedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        file.setUploadedBy(uploader.getId());
        return sharingAgreementFileRepository.save(file);
    }

    /**
     * {@link UUID#compareTo} compares as signed longs and disagrees with PostgreSQL's byte-wise
     * (unsigned) {@code uuid} ordering for roughly half of all id pairs -- mirrors the production
     * tiebreak in {@code GetSharingAgreementRepositoryDatabase} so this test's expectation of "which
     * id wins" actually matches what the DB (and the batch path's merge function) will pick.
     */
    private static int compareIdsAsPostgresDoes(UUID a, UUID b) {
        int cmp = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return cmp != 0 ? cmp : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    // --- findCurrentPublishedAgreementIdByPlantId ---

    @Test
    void findCurrentPublishedAgreementIdByPlantId_returnsEmpty_whenNoAgreementsExist() {
        PlantEntity plant = persistPlant();

        Optional<UUID> result = repository.findCurrentPublishedAgreementIdByPlantId(plant.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findCurrentPublishedAgreementIdByPlantId_returnsLatestPublishedAgreementId() {
        PlantEntity plant = persistPlant();
        persistAgreement(plant, SharingAgreementStatus.PUBLISHED, Instant.now().minusSeconds(120));
        SharingAgreementEntity latestPublished = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, Instant.now());
        // A newer DRAFT must not shadow the latest PUBLISHED one.
        persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now().plusSeconds(60));

        Optional<UUID> result = repository.findCurrentPublishedAgreementIdByPlantId(plant.getId());

        assertTrue(result.isPresent());
        assertEquals(latestPublished.getId(), result.get());
    }

    // --- findById ---

    @Test
    void findById_returnsMappedAgreement_whenExists() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now());

        Optional<SharingAgreement> result = repository.findById(entity.getId());

        assertTrue(result.isPresent());
        SharingAgreement agreement = result.get();
        assertEquals(entity.getId(), agreement.getId());
        assertEquals(plant.getId(), agreement.getPlantId());
        assertEquals(entity.getName(), agreement.getName());
        assertEquals(SharingAgreementStatus.DRAFT, agreement.getStatus());
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        Optional<SharingAgreement> result = repository.findById(UUID.randomUUID());

        assertTrue(result.isEmpty());
    }

    // --- findByPlantId ---

    @Test
    void findByPlantId_withNullStatus_returnsAllAgreementsOrderedNewestFirst() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity older = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now().minusSeconds(60));
        SharingAgreementEntity newer = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, Instant.now());

        List<SharingAgreement> result = repository.findByPlantId(plant.getId(), null);

        assertEquals(2, result.size());
        assertEquals(newer.getId(), result.get(0).getId());
        assertEquals(older.getId(), result.get(1).getId());
    }

    @Test
    void findByPlantId_withStatus_returnsOnlyMatchingAgreements() {
        PlantEntity plant = persistPlant();
        persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now().minusSeconds(60));
        SharingAgreementEntity published = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, Instant.now());

        List<SharingAgreement> result = repository.findByPlantId(plant.getId(), SharingAgreementStatus.PUBLISHED);

        assertEquals(1, result.size());
        assertEquals(published.getId(), result.get(0).getId());
    }

    @Test
    void findByPlantId_scopesResultsToTheGivenPlant() {
        PlantEntity plantA = persistPlant();
        PlantEntity plantB = persistPlant();
        SharingAgreementEntity agreementOfA = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED, Instant.now());
        persistAgreement(plantB, SharingAgreementStatus.PUBLISHED, Instant.now());

        List<SharingAgreement> result = repository.findByPlantId(plantA.getId(), null);

        assertEquals(1, result.size());
        assertEquals(agreementOfA.getId(), result.get(0).getId());
    }

    // --- existsLaterNonDraftAgreement ---

    @Test
    void existsLaterNonDraftAgreement_returnsTrue_whenLaterPublishedAgreementExists() {
        PlantEntity plant = persistPlant();
        Instant t0 = Instant.now();
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, t0);
        persistAgreement(plant, SharingAgreementStatus.PUBLISHED, t0.plusSeconds(60));

        boolean result = repository.existsLaterNonDraftAgreement(plant.getId(), agreement.getId(), t0);

        assertTrue(result);
    }

    @Test
    void existsLaterNonDraftAgreement_returnsFalse_whenOnlyLaterAgreementIsDraft() {
        PlantEntity plant = persistPlant();
        Instant t0 = Instant.now();
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, t0);
        persistAgreement(plant, SharingAgreementStatus.DRAFT, t0.plusSeconds(60));

        boolean result = repository.existsLaterNonDraftAgreement(plant.getId(), agreement.getId(), t0);

        assertFalse(result);
    }

    @Test
    void existsLaterNonDraftAgreement_returnsFalse_whenNoLaterAgreementExists() {
        PlantEntity plant = persistPlant();
        Instant t0 = Instant.now();
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, t0);

        boolean result = repository.existsLaterNonDraftAgreement(plant.getId(), agreement.getId(), t0);

        assertFalse(result);
    }

    @Test
    void existsLaterNonDraftAgreement_excludesTheAgreementItself() {
        PlantEntity plant = persistPlant();
        Instant t0 = Instant.now();
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED, t0);

        // afterCreatedAt intentionally set before the agreement's own createdAt, so only a bug that
        // fails to exclude the agreement's own id (rather than the createdAt filter) would pass.
        boolean result = repository.existsLaterNonDraftAgreement(
                plant.getId(), agreement.getId(), t0.minusSeconds(1));

        assertFalse(result);
    }

    // --- file metadata on findById ---

    @Test
    void findById_hasNullFile_whenNoFileUploaded() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now());

        SharingAgreement agreement = repository.findById(entity.getId()).orElseThrow();

        assertNull(agreement.getFile());
    }

    @Test
    void findById_hasFileSummary_whenOneFileUploaded() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now());
        SharingAgreementFileEntity file = persistFile(entity, UUID.randomUUID(), "distributor.txt", Instant.now());

        SharingAgreement agreement = repository.findById(entity.getId()).orElseThrow();

        assertEquals(file.getId(), agreement.getFile().getId());
        assertEquals("distributor.txt", agreement.getFile().getFilename());
        assertEquals(file.getUploadedAt(), agreement.getFile().getUploadedAt());
    }

    @Test
    void findById_resolvesLatestFile_whenMultipleFilesUploaded() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now());
        persistFile(entity, UUID.randomUUID(), "older.txt", Instant.now().minusSeconds(60));
        SharingAgreementFileEntity latest = persistFile(entity, UUID.randomUUID(), "newer.txt", Instant.now());

        SharingAgreement agreement = repository.findById(entity.getId()).orElseThrow();

        assertEquals(latest.getId(), agreement.getFile().getId());
        assertEquals("newer.txt", agreement.getFile().getFilename());
    }

    @Test
    void findById_breaksTieOnHighestId_whenFilesShareUploadedAt() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now());
        Instant tiedInstant = Instant.now();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID higherId = compareIdsAsPostgresDoes(idA, idB) >= 0 ? idA : idB;
        UUID lowerId = higherId.equals(idA) ? idB : idA;
        persistFile(entity, lowerId, "lower-id.txt", tiedInstant);
        persistFile(entity, higherId, "higher-id.txt", tiedInstant);

        SharingAgreement agreement = repository.findById(entity.getId()).orElseThrow();

        assertEquals(higherId, agreement.getFile().getId());
        assertEquals("higher-id.txt", agreement.getFile().getFilename());
    }

    // --- file metadata on findByPlantId (list) ---

    @Test
    void findByPlantId_resolvesFilePerAgreementIndependently() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity withFile = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now().minusSeconds(60));
        SharingAgreementFileEntity file = persistFile(withFile, UUID.randomUUID(), "distributor.txt", Instant.now());
        SharingAgreementEntity withoutFile = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now());

        List<SharingAgreement> result = repository.findByPlantId(plant.getId(), null);

        SharingAgreement withFileResult = result.stream().filter(a -> a.getId().equals(withFile.getId())).findFirst().orElseThrow();
        SharingAgreement withoutFileResult = result.stream().filter(a -> a.getId().equals(withoutFile.getId())).findFirst().orElseThrow();
        assertEquals(file.getId(), withFileResult.getFile().getId());
        assertEquals("distributor.txt", withFileResult.getFile().getFilename());
        assertNull(withoutFileResult.getFile());
    }

    @Test
    void findByPlantId_breaksTieOnHighestId_whenFilesShareUploadedAt() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity entity = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now());
        Instant tiedInstant = Instant.now();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID higherId = compareIdsAsPostgresDoes(idA, idB) >= 0 ? idA : idB;
        UUID lowerId = higherId.equals(idA) ? idB : idA;
        persistFile(entity, lowerId, "lower-id.txt", tiedInstant);
        persistFile(entity, higherId, "higher-id.txt", tiedInstant);

        List<SharingAgreement> result = repository.findByPlantId(plant.getId(), null);

        assertEquals(1, result.size());
        assertEquals(higherId, result.get(0).getFile().getId());
        assertEquals("higher-id.txt", result.get(0).getFile().getFilename());
    }

    // --- query count: the file lookup must add exactly one query to findByPlantId, regardless of
    // list size, isolated from the pre-existing per-row lazy `plant` load (a separate, already-
    // existing defect this test deliberately does not try to fix or hide) ---

    @Test
    void findByPlantId_addsExactlyOneQuery_forASingleAgreementList() {
        assertFileBatchQueryAddsExactlyOne(1);
    }

    @Test
    void findByPlantId_addsExactlyOneQuery_forAFiveAgreementList() {
        assertFileBatchQueryAddsExactlyOne(5);
    }

    private void assertFileBatchQueryAddsExactlyOne(int agreementCount) {
        PlantEntity plant = persistPlant();
        for (int i = 0; i < agreementCount; i++) {
            SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.DRAFT, Instant.now().minusSeconds(i));
            persistFile(agreement, UUID.randomUUID(), "file-" + i + ".txt", Instant.now());
        }
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();

        // Baseline: exactly what findByPlantId did before this change -- load the agreements and
        // force the (pre-existing, unrelated) per-row lazy `plant` load, with no file lookup at all.
        statistics.clear();
        List<SharingAgreementEntity> entities = sharingAgreementJpaRepository.findByPlantIdOrderByCreatedAtDesc(plant.getId());
        sharingAgreementEntityMapper.mapList(entities);
        long baselineCount = statistics.getPrepareStatementCount();

        // Detach everything so the real call below re-hydrates from scratch, exactly as comparable
        // to the baseline above -- otherwise Hibernate's first-level cache would reuse the plant
        // proxies already initialized by the baseline and hide their cost from this measurement.
        entityManager.clear();

        statistics.clear();
        repository.findByPlantId(plant.getId(), null);
        long afterCount = statistics.getPrepareStatementCount();

        assertEquals(1, afterCount - baselineCount,
                "the file-summary batch lookup must add exactly one query regardless of list size");
    }
}
