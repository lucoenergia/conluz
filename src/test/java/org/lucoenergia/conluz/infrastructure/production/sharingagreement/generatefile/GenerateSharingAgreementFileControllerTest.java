package org.lucoenergia.conluz.infrastructure.production.sharingagreement.generatefile;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileEntry;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileParseResult;
import org.lucoenergia.conluz.domain.production.sharingagreement.distributorfile.DistributorFileParser;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyPartitionCoefficientJpaRepository;
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
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GenerateSharingAgreementFileControllerTest extends BaseControllerTest {

    private static final String REGULATORY_CODE = "ES0031300325733001FH0FA000";
    private static final String CUPS_1 = "ES0031300325733001FH0F";
    private static final String CUPS_2 = "ES0031300325733002FH0F";
    private static final String CUPS_3 = "ES0031300325733003FH0F";

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
    private SupplyPartitionCoefficientJpaRepository supplyPartitionCoefficientJpaRepository;
    @Autowired
    private DistributorFileParser distributorFileParser;

    private CommunityEntity communityA;
    private CommunityEntity communityB;
    private PlantEntity plantA;
    private PlantEntity otherPlantInCommunityA;
    private SharingAgreementEntity draftAgreement;
    private SupplyEntity supply1;
    private SupplyEntity supply2;
    private SupplyEntity supply3;

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
        PlantEntity plant = PlantMother.randomPlantEntity().withSupply(supply).withRegulatoryCode(regulatoryCode).build();
        return plantRepository.save(plant);
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

    private void seedCoefficient(SupplyEntity supply, PlantEntity plant, SharingAgreementEntity agreement,
                                  BigDecimal value, Instant validFrom, Instant validTo) {
        SupplyPartitionCoefficientEntity coefficient = new SupplyPartitionCoefficientEntity();
        coefficient.setId(UUID.randomUUID());
        coefficient.setSupply(supply);
        coefficient.setPlant(plant);
        coefficient.setSharingAgreement(agreement);
        coefficient.setCoefficient(value);
        coefficient.setValidFrom(validFrom);
        coefficient.setValidTo(validTo);
        coefficient.setCreatedAt(Instant.now());
        supplyPartitionCoefficientJpaRepository.save(coefficient);
    }

    private void seedBalancedPendingCoefficients(PlantEntity plant, SharingAgreementEntity agreement) {
        seedCoefficient(supply1, plant, agreement, new BigDecimal("0.333333"), null, null);
        seedCoefficient(supply2, plant, agreement, new BigDecimal("0.333333"), null, null);
        seedCoefficient(supply3, plant, agreement, new BigDecimal("0.333334"), null, null);
    }

    private void setUpBaseFixture() {
        communityA = persistCommunity();
        communityB = persistCommunity();
        UserEntity user = persistUser();
        supply1 = persistSupply(user, communityA, CUPS_1);
        supply2 = persistSupply(user, communityA, CUPS_2);
        supply3 = persistSupply(user, communityA, CUPS_3);
        plantA = persistPlant(supply1, REGULATORY_CODE);
        otherPlantInCommunityA = persistPlant(persistSupply(user, communityA, "ES0031300325733009FH0F"), "OTHER0000000000000000000A");
        draftAgreement = persistAgreement(plantA, SharingAgreementStatus.DRAFT);
    }

    private String url(UUID plantId, UUID agreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId + "/generate-file";
    }

    private String body(Object year) {
        return "{\"year\": " + year + "}";
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        setUpBaseFixture();

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityMember(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForCrossCommunityAdmin() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityB.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenAgreementBelongsToAnotherPlantOfTheSameCommunity() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(otherPlantInCommunityA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenAgreementHasNoCoefficients() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void returnsConflictWhenSumIsNotOne() throws Exception {
        setUpBaseFixture();
        seedCoefficient(supply1, plantA, draftAgreement, new BigDecimal("0.500000"), null, null);
        seedCoefficient(supply2, plantA, draftAgreement, new BigDecimal("0.400000"), null, null);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void returnsConflictWhenSumIsWithinOldToleranceButNotExactlyOne() throws Exception {
        setUpBaseFixture();
        // Sums to 1.000050 -- would have passed the old 0.0001-tolerance warning check, but
        // generation now uses the parser's strict 1.000000 equality, so this must be rejected.
        seedCoefficient(supply1, plantA, draftAgreement, new BigDecimal("0.500025"), null, null);
        seedCoefficient(supply2, plantA, draftAgreement, new BigDecimal("0.500025"), null, null);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void returnsConflictWhenPlantHasNullRegulatoryCode() throws Exception {
        setUpBaseFixture();
        PlantEntity plantWithoutCau = persistPlant(persistSupply(persistUser(), communityA, "ES0031300325733010FH0F"), null);
        SharingAgreementEntity agreement = persistAgreement(plantWithoutCau, SharingAgreementStatus.DRAFT);
        seedCoefficient(supply1, plantWithoutCau, agreement, BigDecimal.ONE, null, null);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantWithoutCau.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void returnsConflictWhenPlantHasBlankRegulatoryCode() throws Exception {
        setUpBaseFixture();
        PlantEntity plantWithBlankCau = persistPlant(persistSupply(persistUser(), communityA, "ES0031300325733011FH0F"), "   ");
        SharingAgreementEntity agreement = persistAgreement(plantWithBlankCau, SharingAgreementStatus.DRAFT);
        seedCoefficient(supply1, plantWithBlankCau, agreement, BigDecimal.ONE, null, null);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantWithBlankCau.getId(), agreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isConflict());
    }

    @Test
    void returnsBadRequestWhenYearIsMissing() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenYearIsNotFourDigits() throws Exception {
        setUpBaseFixture();
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999)))
                .andDo(print())
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(20260)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatesFileForDraftAgreementWithPendingCoefficientsAndRoundTripsThroughTheParser() throws Exception {
        setUpBaseFixture();
        seedBalancedPendingCoefficients(plantA, draftAgreement);
        String authHeader = loginAsCommunityAdmin(communityA.getId());
        String expectedFilename = REGULATORY_CODE + "_2023.txt";

        byte[] responseBytes = mockMvc.perform(post(url(plantA.getId(), draftAgreement.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(expectedFilename)))
                .andReturn().getResponse().getContentAsByteArray();

        String content = new String(responseBytes, StandardCharsets.UTF_8);
        assertEquals(CUPS_1 + ";0,333333\n" + CUPS_2 + ";0,333333\n" + CUPS_3 + ";0,333334\n", content);

        DistributorFileParseResult result = distributorFileParser.parse(expectedFilename, responseBytes,
                REGULATORY_CODE, Set.of(CUPS_1, CUPS_2, CUPS_3));
        assertTrue(result.isValid());
        assertEquals(3, result.getEntries().size());
        for (DistributorFileEntry entry : result.getEntries()) {
            if (entry.getCups().equals(CUPS_1)) {
                assertEquals(0, new BigDecimal("0.333333").compareTo(entry.getCoefficient()));
            } else if (entry.getCups().equals(CUPS_2)) {
                assertEquals(0, new BigDecimal("0.333333").compareTo(entry.getCoefficient()));
            } else if (entry.getCups().equals(CUPS_3)) {
                assertEquals(0, new BigDecimal("0.333334").compareTo(entry.getCoefficient()));
            }
        }
    }

    @Test
    void generatesFileForPublishedAgreement() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity published = persistAgreement(plantA, SharingAgreementStatus.PUBLISHED);
        seedBalancedPendingCoefficients(plantA, published);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), published.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void generatesFileForSupersededAgreement() throws Exception {
        setUpBaseFixture();
        SharingAgreementEntity superseded = persistAgreement(plantA, SharingAgreementStatus.SUPERSEDED);
        seedBalancedPendingCoefficients(plantA, superseded);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        mockMvc.perform(post(url(plantA.getId(), superseded.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2023)))
                .andDo(print())
                .andExpect(status().isOk());
    }
}
