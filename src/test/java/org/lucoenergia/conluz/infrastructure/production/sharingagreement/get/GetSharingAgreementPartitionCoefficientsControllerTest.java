package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
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
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetSharingAgreementPartitionCoefficientsControllerTest extends BaseControllerTest {

    private static final String CUPS_CLOSED = "ES0031300325733001AA0F";
    private static final String CUPS_DERIVED = "ES0031300325733002AA0F";
    private static final String CUPS_PENDING_SUCCESSION = "ES0031300325733003AA0F";
    private static final String CUPS_ORPHAN_DRAFT_REGRESSION = "ES0031300325733004AA0F";
    private static final String CUPS_ORPHAN_NEVER_ACTIVATED = "ES0031300325733005AA0F";

    private static final Instant AGREEMENT_1_CREATED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant AGREEMENT_2_CREATED_AT = Instant.parse("2020-02-01T00:00:00Z");
    private static final Instant AGREEMENT_3_DRAFT_CREATED_AT = Instant.parse("2020-03-01T00:00:00Z");

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
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;

    private CommunityEntity communityA;
    private CommunityEntity communityB;
    private PlantEntity plantA;
    private PlantEntity otherPlantInCommunityA;
    private SharingAgreementEntity agreement1;
    private SharingAgreementEntity agreement2;

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    private UserEntity persistUser() {
        return userRepository.save(UserMother.randomUserEntity());
    }

    private SupplyEntity persistSupply(UserEntity user, CommunityEntity community, String cups, String name) {
        SupplyEntity supply = SupplyEntityMother.random(user, community);
        supply.setCode(cups);
        supply.setName(name);
        return supplyRepository.save(supply);
    }

    private PlantEntity persistPlant(SupplyEntity supply) {
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
        return sharingAgreementRepository.save(agreement);
    }

    private void persistCoefficient(SupplyEntity supply, PlantEntity plant, SharingAgreementEntity agreement,
                                     Instant validFrom, Instant validTo) {
        saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.valueOf(0.5))
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/partition-coefficients";
    }

    /**
     * One plant, three agreements (two PUBLISHED, one DRAFT) and five supplies, each engineered to
     * land in a different endState -- inserted out of CUPS order to make the ordering assertion
     * meaningful. See the endState-by-supply mapping documented on each persist call below.
     */
    private void setUpMultiAgreementFixture() {
        communityA = persistCommunity();
        communityB = persistCommunity();
        UserEntity user = persistUser();

        SupplyEntity supplyOrphanNeverActivated = persistSupply(user, communityA, CUPS_ORPHAN_NEVER_ACTIVATED, "Supply 5");
        SupplyEntity supplyOrphanDraftRegression = persistSupply(user, communityA, CUPS_ORPHAN_DRAFT_REGRESSION, "Supply 4");
        SupplyEntity supplyPendingSuccession = persistSupply(user, communityA, CUPS_PENDING_SUCCESSION, "Supply 3");
        SupplyEntity supplyDerived = persistSupply(user, communityA, CUPS_DERIVED, "Supply 2");
        SupplyEntity supplyClosed = persistSupply(user, communityA, CUPS_CLOSED, "Supply 1");

        plantA = persistPlant(supplyClosed);
        otherPlantInCommunityA = persistPlant(persistSupply(user, communityA, "ES0031300325733009AA0F", "Other supply"));

        agreement1 = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED, AGREEMENT_1_CREATED_AT);
        agreement2 = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED, AGREEMENT_2_CREATED_AT);
        SharingAgreementEntity agreement3Draft = persistAgreement(plantA, SharingAgreementStatus.DRAFT, AGREEMENT_3_DRAFT_CREATED_AT);

        // CLOSED: authored close, no successor row anywhere -- validTo is not cascade-derived.
        persistCoefficient(supplyClosed, plantA, agreement1,
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-06-01T00:00:00Z"));

        // DERIVED: agreement1's validTo was written by the activation cascade -- it equals
        // agreement2's validFrom exactly.
        Instant handoff = Instant.parse("2024-07-01T00:00:00Z");
        persistCoefficient(supplyDerived, plantA, agreement1, Instant.parse("2024-01-01T00:00:00Z"), handoff);
        persistCoefficient(supplyDerived, plantA, agreement2, handoff, null);

        // PENDING_SUCCESSION: agreement1's row is still open (validTo null), and agreement2 has a
        // row for the same supply that exists but has not been applied yet (validFrom null).
        persistCoefficient(supplyPendingSuccession, plantA, agreement1, Instant.parse("2024-01-01T00:00:00Z"), null);
        persistCoefficient(supplyPendingSuccession, plantA, agreement2, null, null);

        // OPEN_ORPHAN (DRAFT-exclusion regression): agreement1's row is open, has no row in
        // agreement2, but DOES have a pending row in agreement3 -- a DRAFT, which must be ignored,
        // so this must resolve to OPEN_ORPHAN (agreement2 is a later non-DRAFT agreement), not
        // PENDING_SUCCESSION (which would wrongly treat the DRAFT row as a real successor).
        persistCoefficient(supplyOrphanDraftRegression, plantA, agreement1, Instant.parse("2024-01-01T00:00:00Z"), null);
        persistCoefficient(supplyOrphanDraftRegression, plantA, agreement3Draft, null, null);

        // OPEN_ORPHAN + applicationState=PENDING (independence of the two fields): never activated
        // at all, but a later non-DRAFT agreement (agreement2) exists for the plant.
        persistCoefficient(supplyOrphanNeverActivated, plantA, agreement1, null, null);
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        setUpMultiAgreementFixture();

        mockMvc.perform(get(url(plantA.getId(), agreement1.getId())))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        setUpMultiAgreementFixture();
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(get(url(plantA.getId(), agreement1.getId())).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        setUpMultiAgreementFixture();
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(get(url(plantA.getId(), agreement1.getId())).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        setUpMultiAgreementFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(get(url(otherPlantInCommunityA.getId(), agreement1.getId())).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsAllFiveEndStatesOrderedByCupsWithZeroToOneScale() throws Exception {
        setUpMultiAgreementFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(get(url(plantA.getId(), agreement1.getId())).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(5)))
                // Response is CUPS-ascending regardless of the scrambled persist order above.
                .andExpect(jsonPath("$[0].supply.code").value(CUPS_CLOSED))
                .andExpect(jsonPath("$[0].endState").value("CLOSED"))
                .andExpect(jsonPath("$[0].applicationState").value("APPLIED"))
                .andExpect(jsonPath("$[0].endDate").value("2024-06-01T00:00:00Z"))
                .andExpect(jsonPath("$[0].coefficient").value(0.5))
                .andExpect(jsonPath("$[1].supply.code").value(CUPS_DERIVED))
                .andExpect(jsonPath("$[1].endState").value("DERIVED"))
                .andExpect(jsonPath("$[1].endDate").value("2024-07-01T00:00:00Z"))
                .andExpect(jsonPath("$[2].supply.code").value(CUPS_PENDING_SUCCESSION))
                .andExpect(jsonPath("$[2].endState").value("PENDING_SUCCESSION"))
                .andExpect(jsonPath("$[2].applicationState").value("APPLIED"))
                .andExpect(jsonPath("$[2].endDate").value(nullValue()))
                .andExpect(jsonPath("$[3].supply.code").value(CUPS_ORPHAN_DRAFT_REGRESSION))
                .andExpect(jsonPath("$[3].endState").value("OPEN_ORPHAN"))
                .andExpect(jsonPath("$[3].endDate").value(nullValue()))
                .andExpect(jsonPath("$[4].supply.code").value(CUPS_ORPHAN_NEVER_ACTIVATED))
                .andExpect(jsonPath("$[4].endState").value("OPEN_ORPHAN"))
                .andExpect(jsonPath("$[4].applicationState").value("PENDING"))
                .andExpect(jsonPath("$[4].endDate").value(nullValue()))
                .andExpect(jsonPath("$[4].supply.name").value("Supply 5"));
    }

    @Test
    void draftExclusionAlsoAppliesToOpenVsOrphanCheck() throws Exception {
        // Querying agreement2 itself: only agreement3 (a DRAFT) is later, so its rows resolve to
        // OPEN, not OPEN_ORPHAN -- proving DRAFT exclusion applies to existsLaterNonDraftAgreement too.
        setUpMultiAgreementFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(get(url(plantA.getId(), agreement2.getId())).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].supply.code").value(CUPS_DERIVED))
                .andExpect(jsonPath("$[0].endState").value("OPEN"))
                .andExpect(jsonPath("$[0].applicationState").value("APPLIED"))
                .andExpect(jsonPath("$[0].endDate").value(nullValue()))
                .andExpect(jsonPath("$[1].supply.code").value(CUPS_PENDING_SUCCESSION))
                .andExpect(jsonPath("$[1].endState").value("OPEN"))
                .andExpect(jsonPath("$[1].applicationState").value("PENDING"))
                .andExpect(jsonPath("$[1].endDate").value(nullValue()));
    }

    @Test
    void worksWhenTargetAgreementItselfIsDraft() throws Exception {
        setUpMultiAgreementFixture();
        SharingAgreementEntity freshDraft = persistAgreement(plantA, SharingAgreementStatus.DRAFT, Instant.now());
        SupplyEntity supply = supplyRepository.findByCode(CUPS_CLOSED).orElseThrow();
        persistCoefficient(supply, plantA, freshDraft, null, null);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(get(url(plantA.getId(), freshDraft.getId())).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].applicationState").value("PENDING"))
                .andExpect(jsonPath("$[0].endState").value("OPEN"));
    }

    @Test
    void worksWhenTargetAgreementItselfIsSuperseded() throws Exception {
        setUpMultiAgreementFixture();
        SharingAgreementEntity superseded = persistAgreement(plantA, SharingAgreementStatus.SUPERSEDED, Instant.now());
        SupplyEntity supply = supplyRepository.findByCode(CUPS_CLOSED).orElseThrow();
        persistCoefficient(supply, plantA, superseded,
                Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-06-01T00:00:00Z"));
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(get(url(plantA.getId(), superseded.getId())).header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].applicationState").value("APPLIED"))
                .andExpect(jsonPath("$[0].endState").value("CLOSED"))
                .andExpect(jsonPath("$[0].endDate").value("2023-06-01T00:00:00Z"));
    }
}
