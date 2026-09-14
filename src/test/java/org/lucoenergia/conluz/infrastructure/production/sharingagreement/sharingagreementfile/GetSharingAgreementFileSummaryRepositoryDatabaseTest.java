package org.lucoenergia.conluz.infrastructure.production.sharingagreement.sharingagreementfile;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFileSummary;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.ContentHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;

@Transactional
class GetSharingAgreementFileSummaryRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSharingAgreementFileSummaryRepositoryDatabase repository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
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

    private PlantEntity persistPlant() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);
        SupplyEntity supply = supplyRepository.save(SupplyEntityMother.random(
                user, communityJpaRepository.getReferenceById(DEFAULT_COMMUNITY_ID)));
        return plantRepository.save(PlantMother.randomPlantEntity().withSupply(supply).build());
    }

    private SharingAgreementEntity persistAgreement(PlantEntity plant) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.DRAFT);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
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
        // value read back later compares equal to what the test asserts.
        file.setUploadedAt(uploadedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        file.setUploadedBy(uploader.getId());
        return sharingAgreementFileRepository.save(file);
    }

    private static int compareIdsAsPostgresDoes(UUID a, UUID b) {
        int cmp = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return cmp != 0 ? cmp : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    // --- findLatestBySharingAgreementId ---

    @Test
    void findLatestBySharingAgreementId_returnsEmpty_whenNoFileUploaded() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity agreement = persistAgreement(plant);

        Optional<SharingAgreementFileSummary> result = repository.findLatestBySharingAgreementId(agreement.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findLatestBySharingAgreementId_returnsSummary_whenOneFileUploaded() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity agreement = persistAgreement(plant);
        SharingAgreementFileEntity file = persistFile(agreement, UUID.randomUUID(), "distributor.txt", Instant.now());

        SharingAgreementFileSummary summary = repository.findLatestBySharingAgreementId(agreement.getId()).orElseThrow();

        assertEquals(file.getId(), summary.getId());
        assertEquals(agreement.getId(), summary.getSharingAgreementId());
        assertEquals("distributor.txt", summary.getFilename());
        assertEquals(file.getUploadedAt(), summary.getUploadedAt());
    }

    @Test
    void findLatestBySharingAgreementId_resolvesLatest_whenMultipleFilesUploaded() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity agreement = persistAgreement(plant);
        persistFile(agreement, UUID.randomUUID(), "older.txt", Instant.now().minusSeconds(60));
        SharingAgreementFileEntity latest = persistFile(agreement, UUID.randomUUID(), "newer.txt", Instant.now());

        SharingAgreementFileSummary summary = repository.findLatestBySharingAgreementId(agreement.getId()).orElseThrow();

        assertEquals(latest.getId(), summary.getId());
        assertEquals("newer.txt", summary.getFilename());
    }

    @Test
    void findLatestBySharingAgreementId_breaksTieOnHighestId_whenFilesShareUploadedAt() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity agreement = persistAgreement(plant);
        Instant tiedInstant = Instant.now();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID higherId = compareIdsAsPostgresDoes(idA, idB) >= 0 ? idA : idB;
        UUID lowerId = higherId.equals(idA) ? idB : idA;
        persistFile(agreement, lowerId, "lower-id.txt", tiedInstant);
        persistFile(agreement, higherId, "higher-id.txt", tiedInstant);

        SharingAgreementFileSummary summary = repository.findLatestBySharingAgreementId(agreement.getId()).orElseThrow();

        assertEquals(higherId, summary.getId());
        assertEquals("higher-id.txt", summary.getFilename());
    }

    // --- findLatestBySharingAgreementIds ---

    @Test
    void findLatestBySharingAgreementIds_returnsEmpty_whenIdsAreEmpty() {
        List<SharingAgreementFileSummary> result = repository.findLatestBySharingAgreementIds(Set.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void findLatestBySharingAgreementIds_resolvesEachAgreementIndependently() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity withFile = persistAgreement(plant);
        SharingAgreementFileEntity file = persistFile(withFile, UUID.randomUUID(), "distributor.txt", Instant.now());
        SharingAgreementEntity withoutFile = persistAgreement(plant);

        List<SharingAgreementFileSummary> result = repository.findLatestBySharingAgreementIds(
                Set.of(withFile.getId(), withoutFile.getId()));

        assertEquals(1, result.size());
        assertEquals(file.getId(), result.get(0).getId());
        assertEquals(withFile.getId(), result.get(0).getSharingAgreementId());
    }

    @Test
    void findLatestBySharingAgreementIds_scopesToGivenIds() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity included = persistAgreement(plant);
        persistFile(included, UUID.randomUUID(), "included.txt", Instant.now());
        SharingAgreementEntity excluded = persistAgreement(plant);
        persistFile(excluded, UUID.randomUUID(), "excluded.txt", Instant.now());

        List<SharingAgreementFileSummary> result = repository.findLatestBySharingAgreementIds(Set.of(included.getId()));

        assertEquals(1, result.size());
        assertEquals(included.getId(), result.get(0).getSharingAgreementId());
    }

    @Test
    void findLatestBySharingAgreementIds_breaksTieOnHighestId_whenFilesShareUploadedAt() {
        PlantEntity plant = persistPlant();
        SharingAgreementEntity agreement = persistAgreement(plant);
        Instant tiedInstant = Instant.now();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID higherId = compareIdsAsPostgresDoes(idA, idB) >= 0 ? idA : idB;
        UUID lowerId = higherId.equals(idA) ? idB : idA;
        persistFile(agreement, lowerId, "lower-id.txt", tiedInstant);
        persistFile(agreement, higherId, "higher-id.txt", tiedInstant);

        List<SharingAgreementFileSummary> result = repository.findLatestBySharingAgreementIds(Set.of(agreement.getId()));

        assertEquals(1, result.size());
        assertEquals(higherId, result.get(0).getId());
        assertEquals("higher-id.txt", result.get(0).getFilename());
    }
}
