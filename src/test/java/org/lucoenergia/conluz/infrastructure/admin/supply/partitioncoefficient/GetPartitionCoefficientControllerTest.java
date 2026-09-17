package org.lucoenergia.conluz.infrastructure.admin.supply.partitioncoefficient;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
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
import java.util.List;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetPartitionCoefficientControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository partitionCoefficientRepository;
    @Autowired
    private GetCommunityRepository getCommunityRepository;
    @Autowired
    private SupplyRepository supplyJpaRepository;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void getHistoryReturnsAllPeriodsOrdered() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(1.0), t0, t1);
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(2.0), t1, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plant.id").value(agreement.getPlant().getId().toString()))
                .andExpect(jsonPath("$[0].plant.name").value(agreement.getPlant().getName()))
                .andExpect(jsonPath("$[0].supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$[0].supply.code").value(supply.getCode()))
                .andExpect(jsonPath("$[0].community.id").value(DEFAULT_COMMUNITY_ID.toString()))
                .andExpect(jsonPath("$[0].community.name").value(defaultCommunityName()))
                .andExpect(jsonPath("$[0].sharingAgreement.id").value(agreement.getId().toString()))
                .andExpect(jsonPath("$[0].sharingAgreement.name").value(agreement.getName()))
                .andExpect(jsonPath("$[0].sharingAgreement.status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].validFrom").value("2024-01-01T00:00:00Z"))
                .andExpect(jsonPath("$[1].plant.id").value(agreement.getPlant().getId().toString()))
                .andExpect(jsonPath("$[1].validFrom").value("2025-01-01T00:00:00Z"))
                // No flat entity reference survives anywhere in the payload.
                .andExpect(jsonPath("$[0].supplyId").doesNotExist())
                .andExpect(jsonPath("$[0].plantId").doesNotExist())
                .andExpect(jsonPath("$[0].communityId").doesNotExist())
                .andExpect(jsonPath("$[0].sharingAgreementId").doesNotExist());
    }

    @Test
    void getHistoryDisambiguatesRowsAcrossTwoPlantsForSameSupply() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement1 = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity agreement2 = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        // Same [t0, open) window on both plants: without plantId these two rows would be
        // indistinguishable overlapping intervals for the same supply.
        persistCoefficient(supply.getId(), agreement1, BigDecimal.valueOf(0.4), t0, null);
        persistCoefficient(supply.getId(), agreement2, BigDecimal.valueOf(0.6), t0, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].plant.id").value(Matchers.containsInAnyOrder(
                        agreement1.getPlant().getId().toString(), agreement2.getPlant().getId().toString())));
    }

    @Test
    void getActiveReturnsOneOpenPeriodPerPlant() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(1.0), t0, t1);
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(2.0), t1, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/active")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].validTo").doesNotExist())
                .andExpect(jsonPath("$[0].plant.id").value(agreement.getPlant().getId().toString()))
                .andExpect(jsonPath("$[0].supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$[0].sharingAgreement.status").value("PUBLISHED"));
    }

    @Test
    void getActiveReturnsOneItemPerPlantAndExcludesPendingPeriods() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity inX = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity inY = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity draftInX = persistAgreement(inX, SharingAgreementStatus.DRAFT);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), inX, BigDecimal.valueOf(0.4), t0, null);
        persistCoefficient(supply.getId(), inY, BigDecimal.valueOf(0.6), t0, null);
        // Pending: null validFrom. Its validTo is null too, so only the validFrom rule keeps it out.
        persistCoefficient(supply.getId(), draftInX, BigDecimal.valueOf(0.9), null, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/active")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].plant.id").value(Matchers.containsInAnyOrder(
                        inX.getPlant().getId().toString(), inY.getPlant().getId().toString())))
                .andExpect(jsonPath("$[*].validFrom").value(Matchers.everyItem(Matchers.notNullValue())));
    }

    @Test
    void getActiveReturnsEmptyListWhenOnlyPendingPeriodsExist() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity draft = ensurePlantAndAgreement(supply, SharingAgreementStatus.DRAFT);
        persistCoefficient(supply.getId(), draft, BigDecimal.valueOf(1.0), null, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/active")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAtTimestampReturnsCoefficientActiveAtThatInstant() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(3.076300), t0, t1);
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(4.000000), t1, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/at")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("timestamp", "2024-06-15T12:00:00Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].coefficient").value("3.0763"))
                .andExpect(jsonPath("$[0].supply.id").value(supply.getId().toString()))
                .andExpect(jsonPath("$[0].supply.code").value(supply.getCode()))
                .andExpect(jsonPath("$[0].community.id").value(DEFAULT_COMMUNITY_ID.toString()))
                .andExpect(jsonPath("$[0].community.name").value(defaultCommunityName()))
                .andExpect(jsonPath("$[0].plant.id").value(agreement.getPlant().getId().toString()))
                .andExpect(jsonPath("$[0].plant.name").value(agreement.getPlant().getName()))
                .andExpect(jsonPath("$[0].timestamp").value("2024-06-15T12:00:00Z"))
                .andExpect(jsonPath("$[0].supplyId").doesNotExist())
                .andExpect(jsonPath("$[0].plantId").doesNotExist())
                .andExpect(jsonPath("$[0].communityId").doesNotExist());
    }

    @Test
    void getAtTimestampReturnsEmptyListWhenNoHistoryCoversTimestamp() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        // No coefficient seeded for this supply

        // No period covering the instant is a normal answer, not a missing resource: the endpoint
        // returns a collection, and an empty collection is what "nothing applies" looks like.
        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/at")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("timestamp", "2024-06-15T12:00:00Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAtTimestampReturnsOneItemPerPlantCoveringTheInstant() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity inX = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity inY = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), inX, BigDecimal.valueOf(0.4), t0, null);
        persistCoefficient(supply.getId(), inY, BigDecimal.valueOf(0.6), t0, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/at")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("timestamp", "2024-06-15T12:00:00Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].plant.id").value(Matchers.containsInAnyOrder(
                        inX.getPlant().getId().toString(), inY.getPlant().getId().toString())));
    }

    @Test
    void getAtTimestampTreatsValidFromAsInclusiveAndValidToAsExclusive() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        Instant boundary = Instant.parse("2025-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(1.0), t0, boundary);
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(2.0), boundary, null);

        // Exactly at the shared instant the later period applies: validTo is exclusive, validFrom
        // inclusive, so the two consecutive periods never both answer.
        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/at")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("timestamp", "2025-01-01T00:00:00Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].coefficient").value("2.0"));

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/at")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("timestamp", "2024-12-31T23:59:59Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].coefficient").value("1.0"));
    }

    @Test
    void getHistoryRestrictsToOnePlantWhenPlantIdIsGiven() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity inX = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity inY = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), inX, BigDecimal.valueOf(0.4), t0, null);
        persistCoefficient(supply.getId(), inY, BigDecimal.valueOf(0.6), t0, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("plantId", inX.getPlant().getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].plant.id").value(inX.getPlant().getId().toString()));

        // Without the filter both plants' timelines come back.
        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getHistoryReturnsEmptyListForAPlantTheSupplyHasNoCoefficientIn() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity agreement = ensurePlantAndPublishedAgreement(supply);
        persistCoefficient(supply.getId(), agreement, BigDecimal.valueOf(1.0),
                Instant.parse("2024-01-01T00:00:00Z"), null);
        // A plant of a different supply entirely -- unrelated to this supply's timeline.
        SharingAgreementEntity unrelated = ensurePlantAndPublishedAgreement(createTestSupply());

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("plantId", unrelated.getPlant().getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getActiveRestrictsToOnePlantWhenPlantIdIsGiven() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity inX = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity inY = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), inX, BigDecimal.valueOf(0.4), t0, null);
        persistCoefficient(supply.getId(), inY, BigDecimal.valueOf(0.6), t0, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/active")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("plantId", inY.getPlant().getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].plant.id").value(inY.getPlant().getId().toString()));
    }

    @Test
    void getAtTimestampRestrictsToOnePlantWhenPlantIdIsGiven() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Supply supply = createTestSupply();
        SharingAgreementEntity inX = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity inY = ensurePlantAndPublishedAgreement(supply);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), inX, BigDecimal.valueOf(0.4), t0, null);
        persistCoefficient(supply.getId(), inY, BigDecimal.valueOf(0.6), t0, null);

        mockMvc.perform(get("/api/v1/supplies/" + supply.getId() + "/partition-coefficients/at")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .param("timestamp", "2024-06-15T12:00:00Z")
                        .param("plantId", inX.getPlant().getId().toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].plant.id").value(inX.getPlant().getId().toString()));
    }

    // --- Authorization (see docs/security/authorization-policy.md) ---
    //
    // The history is guarded by canReadSupply -- the same rule as GET /supplies/{supplyId} -- so the
    // supply owner may read it. /active and /at keep canEditSupply and stay admin-only. Both guards
    // resolve the community from the supply itself, so a caller who cannot see the supply at all gets
    // 404 rather than 403: its existence must not leak.

    @Test
    void endpointsRejectUnauthenticatedCallersWith401() throws Exception {
        Supply supply = createTestSupply();

        for (String path : coefficientPaths(supply)) {
            mockMvc.perform(get(path).param("timestamp", "2024-06-15T12:00:00Z")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void endpointsReturn404ForAnAdminOfAnotherCommunity() throws Exception {
        Supply supply = createTestSupply();
        CommunityEntity otherCommunity = communityJpaRepository.save(CommunityMother.randomEntity().build());
        String authHeader = loginAsCommunityAdmin(otherCommunity.getId());

        for (String path : coefficientPaths(supply)) {
            mockMvc.perform(get(path).param("timestamp", "2024-06-15T12:00:00Z")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * AC11: widening the history to the owner must not drag the single-result endpoints along. They
     * answer "which coefficient applies", which stays an administrative question.
     */
    @Test
    void activeAndAtTimestampAreReadableByTheSupplyOwnerWhoIsNotAnAdmin() throws Exception {
        // Coefficients say what share of the plant's production is assigned to this supply -- the
        // owner's own share -- so all three reads are open to them, as history always was. Pending
        // periods stay hidden, but that is the read's job: active and at-timestamp exclude them for
        // everybody, so there is nothing here for the guard to protect.
        User owner = UserMother.randomUser();
        owner.enable();
        createUserRepository.create(owner);
        Supply supply = createSupplyService.create(SupplyMother.random(owner).build(),
                UserPersonalId.of(owner.getPersonalId()), DEFAULT_COMMUNITY_ID);
        String authHeader = loginUser(owner);

        String base = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";
        for (String path : List.of(base, base + "/active", base + "/at")) {
            mockMvc.perform(get(path).param("timestamp", "2024-06-15T12:00:00Z")
                            .header(HttpHeaders.AUTHORIZATION, authHeader)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    // --- History: owner access and pending visibility (AC9, AC10) ---

    @Test
    void getHistoryReturnsEveryPlantWithoutPendingPeriodsForTheOwner() throws Exception {
        User owner = UserMother.randomUser();
        owner.enable();
        createUserRepository.create(owner);
        Supply supply = createSupplyService.create(SupplyMother.random(owner).build(),
                UserPersonalId.of(owner.getPersonalId()), DEFAULT_COMMUNITY_ID);
        TwoPlantFixture fixture = twoPlantsWithActivatedAndPendingCoefficients(supply);

        mockMvc.perform(get(historyPath(supply))
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].validFrom").value(Matchers.everyItem(Matchers.notNullValue())))
                .andExpect(jsonPath("$[*].plant.id").value(Matchers.containsInAnyOrder(
                        fixture.plantX().toString(), fixture.plantY().toString())))
                // Pending rows live in a DRAFT agreement, so none of its periods may surface either.
                .andExpect(jsonPath("$[*].sharingAgreement.status")
                        .value(Matchers.everyItem(Matchers.not("DRAFT"))));
    }

    @Test
    void getHistoryHonoursThePlantFilterForTheOwnerAndStillHidesPending() throws Exception {
        User owner = UserMother.randomUser();
        owner.enable();
        createUserRepository.create(owner);
        Supply supply = createSupplyService.create(SupplyMother.random(owner).build(),
                UserPersonalId.of(owner.getPersonalId()), DEFAULT_COMMUNITY_ID);
        TwoPlantFixture fixture = twoPlantsWithActivatedAndPendingCoefficients(supply);

        mockMvc.perform(get(historyPath(supply))
                        .param("plantId", fixture.plantX().toString())
                        .header(HttpHeaders.AUTHORIZATION, loginUser(owner))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].plant.id").value(fixture.plantX().toString()))
                .andExpect(jsonPath("$[0].validFrom").isNotEmpty());
    }

    /**
     * The regression guarding AC1: admins keep the whole timeline, pending periods included. They are
     * the only caller who can act on one.
     */
    @Test
    void getHistoryKeepsPendingPeriodsForAnAdminOfTheSupplyCommunity() throws Exception {
        Supply supply = createTestSupply();
        twoPlantsWithActivatedAndPendingCoefficients(supply);

        mockMvc.perform(get(historyPath(supply))
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].validFrom").value(Matchers.hasItem(Matchers.nullValue())));
    }

    /**
     * AC10. Owning the supply must not narrow what an admin of its community may see -- the two roles
     * are additive, and nothing stops a community admin from owning a supply in their own community.
     */
    @Test
    void getHistoryKeepsPendingPeriodsForAnOwnerWhoIsAlsoAnAdminOfTheSupplyCommunity() throws Exception {
        User ownerAdmin = UserMother.randomUser();
        ownerAdmin.enable();
        createUserRepository.create(ownerAdmin);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, ownerAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);
        Supply supply = createSupplyService.create(SupplyMother.random(ownerAdmin).build(),
                UserPersonalId.of(ownerAdmin.getPersonalId()), DEFAULT_COMMUNITY_ID);
        twoPlantsWithActivatedAndPendingCoefficients(supply);

        mockMvc.perform(get(historyPath(supply))
                        .header(HttpHeaders.AUTHORIZATION, loginUser(ownerAdmin))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].validFrom").value(Matchers.hasItem(Matchers.nullValue())));
    }

    // --- History denials mirror GET /supplies/{supplyId} exactly (AC9) ---
    //
    // Asserted by calling both endpoints in the same test and comparing their statuses to each other.
    // Hardcoding the expected code would let the two drift apart while both tests still passed.

    @Test
    void getHistoryDeniesACommunityMemberExactlyAsGetSupplyDoes() throws Exception {
        Supply supply = createTestSupply();
        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        assertHistoryDeniedLikeGetSupply(supply, authHeader);
    }

    @Test
    void getHistoryDeniesAnAdminOfAnotherCommunityExactlyAsGetSupplyDoes() throws Exception {
        Supply supply = createTestSupply();
        CommunityEntity otherCommunity = communityJpaRepository.save(CommunityMother.randomEntity().build());
        String authHeader = loginAsCommunityAdmin(otherCommunity.getId());

        assertHistoryDeniedLikeGetSupply(supply, authHeader);
    }

    private void assertHistoryDeniedLikeGetSupply(Supply supply, String authHeader) throws Exception {
        int getSupplyStatus = mockMvc.perform(get("/api/v1/supplies/" + supply.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getStatus();
        int historyStatus = mockMvc.perform(get(historyPath(supply))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getStatus();

        assertEquals(getSupplyStatus, historyStatus,
                "the history must answer this caller exactly as GET /supplies/{supplyId} does");
    }

    private String historyPath(Supply supply) {
        return "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";
    }

    /**
     * Two plants, each carrying one activated period in a PUBLISHED agreement and one pending period
     * in a DRAFT one. A pending row can only ever belong to a DRAFT agreement: activation rejects a
     * DRAFT agreement, and revert-to-draft refuses once any coefficient has a validFrom.
     */
    private TwoPlantFixture twoPlantsWithActivatedAndPendingCoefficients(Supply supply) {
        SharingAgreementEntity publishedInX = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity publishedInY = ensurePlantAndPublishedAgreement(supply);
        SharingAgreementEntity draftInX = persistAgreement(publishedInX, SharingAgreementStatus.DRAFT);
        SharingAgreementEntity draftInY = persistAgreement(publishedInY, SharingAgreementStatus.DRAFT);
        Instant t0 = Instant.parse("2024-01-01T00:00:00Z");
        persistCoefficient(supply.getId(), publishedInX, BigDecimal.valueOf(0.4), t0, null);
        persistCoefficient(supply.getId(), publishedInY, BigDecimal.valueOf(0.6), t0, null);
        persistCoefficient(supply.getId(), draftInX, BigDecimal.valueOf(0.45), null, null);
        persistCoefficient(supply.getId(), draftInY, BigDecimal.valueOf(0.55), null, null);
        return new TwoPlantFixture(publishedInX.getPlant().getId(), publishedInY.getPlant().getId());
    }

    private record TwoPlantFixture(UUID plantX, UUID plantY) {
    }

    private List<String> coefficientPaths(Supply supply) {
        String base = "/api/v1/supplies/" + supply.getId() + "/partition-coefficients";
        return List.of(base, base + "/active", base + "/at");
    }

    private String defaultCommunityName() {
        return getCommunityRepository.findById(DEFAULT_COMMUNITY_ID).orElseThrow().getName();
    }

    private Supply createTestSupply() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = SupplyMother.random(user).build();
        return createSupplyService.create(supply, UserPersonalId.of(user.getPersonalId()), DEFAULT_COMMUNITY_ID);
    }

    private SharingAgreementEntity ensurePlantAndPublishedAgreement(Supply supply) {
        return ensurePlantAndAgreement(supply, SharingAgreementStatus.PUBLISHED);
    }

    private SharingAgreementEntity ensurePlantAndAgreement(Supply supply, SharingAgreementStatus status) {
        SupplyEntity supplyEntity = supplyJpaRepository.getReferenceById(supply.getId());
        PlantEntity plant = plantRepository.save(PlantMother.randomPlantEntity().withSupply(supplyEntity).build());
        return persistAgreement(plant, status);
    }

    /**
     * A second agreement on the plant of an existing one -- for fixtures that need a draft and a
     * published agreement over the same plant.
     */
    private SharingAgreementEntity persistAgreement(SharingAgreementEntity onPlantOf, SharingAgreementStatus status) {
        return persistAgreement(onPlantOf.getPlant(), status);
    }

    private SharingAgreementEntity persistAgreement(PlantEntity plant, SharingAgreementStatus status) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plant);
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(status);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        return sharingAgreementRepository.save(agreement);
    }

    private void persistCoefficient(UUID supplyId, SharingAgreementEntity agreement,
                                    BigDecimal coefficient, Instant validFrom, Instant validTo) {
        partitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(agreement.getPlant().getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }
}
