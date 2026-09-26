package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.sharingagreement.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the plant-level "coefficients in force" read endpoint. Every case uses several
 * supplies and more than one agreement: a single-element result proves no selection happened.
 *
 * <p>States the domain <em>computes</em> -- a predecessor's {@code validTo}, and the SUPERSEDED status
 * -- are never persisted here. They are produced by driving the real activation cascade through
 * {@link CoefficientActivationService}, and where a test's premise is such a state, the fixture asserts
 * it reached it. Agreements themselves are persisted directly as PUBLISHED, which is exactly what
 * {@code PublishSharingAgreementRepositoryDatabase} stores; going through the publish service instead
 * would drag in its "the set must sum to exactly 1.000000" precondition, which the deliberate
 * sum-not-1 transition fixture needs to be free of.
 */
@Transactional
class GetPlantActivePartitionCoefficientsControllerTest extends BaseControllerTest {

    private static final LocalDate FIRST_ACTIVATION = LocalDate.parse("2024-01-01");
    private static final LocalDate SECOND_ACTIVATION = LocalDate.parse("2025-01-01");

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private CoefficientActivationService activationService;

    private Community communityA;
    private Community communityB;
    private Plant plant;

    @BeforeEach
    void setUp() {
        communityA = createCommunityRepository.create(CommunityMother.random().build());
        communityB = createCommunityRepository.create(CommunityMother.random().build());
        plant = createPlant(communityA);
    }

    @Test
    void returnsOnlyActiveCoefficientsWhenAPublishedSetCoexistsWithADraft() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        Supply supplyA = createSupply(communityA);
        Supply supplyB = createSupply(communityA);
        Supply supplyC = createSupply(communityA);

        SharingAgreementEntity published = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient inForceA = persistPending(supplyA, published, new BigDecimal("0.400000"));
        SupplyPartitionCoefficient inForceB = persistPending(supplyB, published, new BigDecimal("0.600000"));
        activate(published, FIRST_ACTIVATION, List.of(inForceA.getId(), inForceB.getId()));

        SharingAgreementEntity draft = persistAgreement(plant, SharingAgreementStatus.DRAFT);
        persistPending(supplyC, draft, new BigDecimal("0.100000"));

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].supply.id",
                        containsInAnyOrder(supplyA.getId().toString(), supplyB.getId().toString())))
                .andExpect(jsonPath("$[*].sharingAgreement.id",
                        everyItem(is(published.getId().toString()))));
    }

    @Test
    void excludesPendingCoefficientsOfAPublishedAgreement() throws Exception {
        // A PUBLISHED agreement whose coefficients the distributor has not applied yet must not
        // displace the set still in force -- being PUBLISHED is not what puts a coefficient in force.
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        Supply supplyA = createSupply(communityA);
        Supply supplyB = createSupply(communityA);

        SharingAgreementEntity inForce = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient a = persistPending(supplyA, inForce, new BigDecimal("0.400000"));
        SupplyPartitionCoefficient b = persistPending(supplyB, inForce, new BigDecimal("0.600000"));
        activate(inForce, FIRST_ACTIVATION, List.of(a.getId(), b.getId()));

        SharingAgreementEntity notAppliedYet = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistPending(supplyA, notAppliedYet, new BigDecimal("0.300000"));
        persistPending(supplyB, notAppliedYet, new BigDecimal("0.700000"));

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].sharingAgreement.id",
                        everyItem(is(inForce.getId().toString()))))
                .andExpect(jsonPath("$[*].coefficient",
                        containsInAnyOrder(0.400000, 0.600000)));
    }

    @Test
    void returnsBothWhenTwoPublishedAgreementsEachHoldAnOpenRow() throws Exception {
        // The reachable cross-agreement state: the newest agreement has been applied for one supply
        // only, so the other is still on the previous agreement. Both agreements keep an open row and
        // therefore both remain PUBLISHED.
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        Supply stayingOnFirst = createSupply(communityA);
        Supply movedToSecond = createSupply(communityA);

        SharingAgreementEntity first = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient firstForStaying = persistPending(stayingOnFirst, first, new BigDecimal("0.400000"));
        persistPending(movedToSecond, first, new BigDecimal("0.600000"));
        activate(first, FIRST_ACTIVATION, List.of(firstForStaying.getId()));

        SharingAgreementEntity second = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistPending(stayingOnFirst, second, new BigDecimal("0.300000"));
        SupplyPartitionCoefficient secondForMoved = persistPending(movedToSecond, second, new BigDecimal("0.700000"));
        activate(second, SECOND_ACTIVATION, List.of(secondForMoved.getId()));

        assertEquals(SharingAgreementStatus.PUBLISHED, statusOf(first));
        assertEquals(SharingAgreementStatus.PUBLISHED, statusOf(second));

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].sharingAgreement.id",
                        containsInAnyOrder(first.getId().toString(), second.getId().toString())))
                .andExpect(jsonPath("$[*].sharingAgreement.status", everyItem(is("PUBLISHED"))));
    }

    @Test
    void returnsOnlySuccessorWhenSameSupplyWasSucceeded() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        SuccessionFixture fixture = succeedOneSupplyAcrossTwoAgreements();

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].supply.id", containsInAnyOrder(
                        fixture.succeeded().getId().toString(), fixture.addedLater().getId().toString())))
                // The succeeded supply is reported on its successor, never on the closed predecessor.
                .andExpect(jsonPath("$[*].sharingAgreement.id",
                        everyItem(is(fixture.second().getId().toString()))));
    }

    @Test
    void excludesClosedCoefficientsOfASupersededAgreement() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        SuccessionFixture fixture = succeedOneSupplyAcrossTwoAgreements();

        // The premise of this test is a derived state, so the fixture proves it reached it: the
        // cascade closed the only row of the first agreement, and recomputeStatus therefore made it
        // SUPERSEDED. Nothing here persisted that status.
        assertEquals(SharingAgreementStatus.SUPERSEDED, statusOf(fixture.first()));

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].sharingAgreement.status", everyItem(is("PUBLISHED"))))
                .andExpect(jsonPath("$[*].validTo", everyItem(is((Object) null))));
    }

    @Test
    void returnsEveryCoefficientDuringATransitionWithoutNormalising() throws Exception {
        // A partially applied transition: Sigma(beta) != 1 is legitimate and must be reported as
        // stored, neither normalised nor rejected.
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        Supply notMigratedYet = createSupply(communityA);
        Supply alreadyMigrated = createSupply(communityA);

        SharingAgreementEntity first = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient firstForNotMigrated =
                persistPending(notMigratedYet, first, new BigDecimal("0.400000"));
        SupplyPartitionCoefficient firstForMigrated =
                persistPending(alreadyMigrated, first, new BigDecimal("0.600000"));
        activate(first, FIRST_ACTIVATION, List.of(firstForNotMigrated.getId(), firstForMigrated.getId()));

        SharingAgreementEntity second = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistPending(notMigratedYet, second, new BigDecimal("0.300000"));
        SupplyPartitionCoefficient secondForMigrated =
                persistPending(alreadyMigrated, second, new BigDecimal("0.700000"));
        activate(second, SECOND_ACTIVATION, List.of(secondForMigrated.getId()));

        // The first agreement keeps an open row, so it is still PUBLISHED rather than SUPERSEDED.
        assertEquals(SharingAgreementStatus.PUBLISHED, statusOf(first));

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // 0.4 from the old agreement plus 0.7 from the new one: 1.1, reported untouched.
                .andExpect(jsonPath("$[*].coefficient", containsInAnyOrder(0.400000, 0.700000)))
                .andExpect(jsonPath("$[*].supply.id", containsInAnyOrder(
                        notMigratedYet.getId().toString(), alreadyMigrated.getId().toString())))
                .andExpect(jsonPath("$[*].sharingAgreement.id",
                        containsInAnyOrder(first.getId().toString(), second.getId().toString())));
    }

    @Test
    void returnsOnlyTheRequestedPlantForASupplyActiveInTwoPlants() throws Exception {
        // no_overlapping_coefficients is scoped to (plant_id, supply_id), so one supply may hold an
        // open coefficient in two plants at once. Each plant also gets a supply of its own, so the
        // response proves selection by plant rather than merely returning everything.
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        Plant plantX = plant;
        Plant plantY = createPlant(communityA);
        Supply shared = createSupply(communityA);
        Supply onlyInX = createSupply(communityA);
        Supply onlyInY = createSupply(communityA);

        SharingAgreementEntity agreementX = persistAgreement(plantX, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient sharedInX = persistPending(shared, agreementX, new BigDecimal("0.500000"));
        SupplyPartitionCoefficient xOwn = persistPending(onlyInX, agreementX, new BigDecimal("0.500000"));
        activate(agreementX, FIRST_ACTIVATION, List.of(sharedInX.getId(), xOwn.getId()));

        SharingAgreementEntity agreementY = persistAgreement(plantY, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient sharedInY = persistPending(shared, agreementY, new BigDecimal("0.500000"));
        SupplyPartitionCoefficient yOwn = persistPending(onlyInY, agreementY, new BigDecimal("0.500000"));
        activate(agreementY, FIRST_ACTIVATION, List.of(sharedInY.getId(), yOwn.getId()));

        mockMvc.perform(get(url(plantX.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].supply.id", containsInAnyOrder(
                        shared.getId().toString(), onlyInX.getId().toString())))
                .andExpect(jsonPath("$[*].plant.id", everyItem(is(plantX.getId().toString()))));

        mockMvc.perform(get(url(plantY.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].supply.id", containsInAnyOrder(
                        shared.getId().toString(), onlyInY.getId().toString())))
                .andExpect(jsonPath("$[*].plant.id", everyItem(is(plantY.getId().toString()))));
    }

    @Test
    void ordersByCupsAscending() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        String firstCups = "ES0000000000000000001AA";
        String secondCups = "ES0000000000000000002BB";
        String thirdCups = "ES0000000000000000003CC";
        // Persisted in an order that is not the expected one, so passing cannot be an accident.
        Supply third = createSupply(communityA, thirdCups);
        Supply first = createSupply(communityA, firstCups);
        Supply second = createSupply(communityA, secondCups);

        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient c3 = persistPending(third, agreement, new BigDecimal("0.300000"));
        SupplyPartitionCoefficient c1 = persistPending(first, agreement, new BigDecimal("0.400000"));
        SupplyPartitionCoefficient c2 = persistPending(second, agreement, new BigDecimal("0.300000"));
        activate(agreement, FIRST_ACTIVATION, List.of(c3.getId(), c1.getId(), c2.getId()));

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].supply.code", contains(firstCups, secondCups, thirdCups)));
    }

    @Test
    void returnsEmptyListWhenPlantHasNoCoefficientInForce() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        SharingAgreementEntity agreement = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        persistPending(createSupply(communityA), agreement, new BigDecimal("0.400000"));
        persistPending(createSupply(communityA), agreement, new BigDecimal("0.600000"));

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get(url(plant.getId()))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotFoundForUnknownPlant() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(get(url(UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForCrossCommunityMember() throws Exception {
        // A member of another community cannot see the plant -> 404, not 403.
        String authHeader = loginAsCommunityMember(communityB.getId());

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForCommunityAdminOfAnotherCommunity() throws Exception {
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        // The caller can see the plant, so its existence is not a secret from them; they simply may
        // not read agreement content -> 403.
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(get(url(plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    /**
     * One supply activated in a first agreement and then succeeded by a second, plus a supply the
     * second agreement adds. The cascade writes the predecessor's {@code validTo} and recomputes both
     * agreements' statuses, so the first agreement ends up SUPERSEDED without anyone storing that.
     */
    private SuccessionFixture succeedOneSupplyAcrossTwoAgreements() {
        Supply succeeded = createSupply(communityA);
        Supply addedLater = createSupply(communityA);

        SharingAgreementEntity first = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient firstRow = persistPending(succeeded, first, new BigDecimal("1.000000"));
        activate(first, FIRST_ACTIVATION, List.of(firstRow.getId()));

        SharingAgreementEntity second = persistAgreement(plant, SharingAgreementStatus.PUBLISHED);
        SupplyPartitionCoefficient secondRowForSucceeded =
                persistPending(succeeded, second, new BigDecimal("0.600000"));
        SupplyPartitionCoefficient secondRowForAdded =
                persistPending(addedLater, second, new BigDecimal("0.400000"));
        activate(second, SECOND_ACTIVATION,
                List.of(secondRowForSucceeded.getId(), secondRowForAdded.getId()));

        return new SuccessionFixture(succeeded, addedLater, first, second);
    }

    private record SuccessionFixture(Supply succeeded, Supply addedLater,
                                     SharingAgreementEntity first, SharingAgreementEntity second) {
    }

    private Plant createPlant(Community community) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply supply = SupplyMother.random(owner).build();
        supply = createSupplyRepository.create(supply, UserId.of(owner.getId()), community.getId());
        Plant newPlant = PlantMother.random(supply).build();
        return createPlantRepository.create(newPlant, SupplyId.of(supply.getId()));
    }

    private Supply createSupply(Community community) {
        return createSupply(community, null);
    }

    private Supply createSupply(Community community, String code) {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        Supply.Builder builder = SupplyMother.random(owner);
        if (code != null) {
            builder.withCode(code);
        }
        return createSupplyRepository.create(builder.build(), UserId.of(owner.getId()), community.getId());
    }

    private SharingAgreementEntity persistAgreement(Plant onPlant, SharingAgreementStatus status) {
        PlantEntity plantEntity = plantRepository.getReferenceById(onPlant.getId());
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plantEntity);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    /**
     * A pending row, exactly as materialisation writes one: no validFrom, no validTo. Activation is
     * then driven through the real cascade.
     */
    private SupplyPartitionCoefficient persistPending(Supply supply, SharingAgreementEntity agreement,
                                                      BigDecimal coefficient) {
        return saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(agreement.getPlant().getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(coefficient)
                .withValidFrom(null)
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());
    }

    private void activate(SharingAgreementEntity agreement, LocalDate appliedOn, List<UUID> coefficientIds) {
        activationService.setValidFrom(agreement.getPlant().getId(), agreement.getId(), appliedOn, coefficientIds);
    }

    private SharingAgreementStatus statusOf(SharingAgreementEntity agreement) {
        return sharingAgreementRepository.findById(agreement.getId()).orElseThrow().getStatus();
    }

    private String url(UUID plantId) {
        return "/api/v1/plants/" + plantId + "/partition-coefficients/active";
    }
}
