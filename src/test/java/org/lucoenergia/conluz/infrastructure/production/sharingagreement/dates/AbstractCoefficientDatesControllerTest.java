package org.lucoenergia.conluz.infrastructure.production.sharingagreement.dates;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.activation.CoefficientActivationService;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.publish.PublishSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the write and the preview of an agreement's coefficient dates share: the same guard, the same body and the same
 * error payloads (SHA-014). Each subclass runs every test against its own operation.
 *
 * <p>Not transactional, so that no session stays open through the request. Every test builds its own community, so
 * rows committed by other tests never meet it.
 */
abstract class AbstractCoefficientDatesControllerTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository userCreator;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private PublishSharingAgreementRepository publishSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    protected GetSupplyPartitionCoefficientRepository getCoefficientRepository;
    @Autowired
    private CoefficientActivationService activationService;

    protected Community community;
    protected Plant plant;
    protected Supply x;
    protected Supply y;
    protected Supply z;
    protected Agreement a;
    protected Agreement b;

    /** The last path segment of the operation under test. */
    protected abstract String operation();

    /**
     * A open for X, Y and Z since 2025-01-01; B holds X and Y, pending.
     */
    @BeforeEach
    void setUpTimeline() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        plant = createPlant(community);
        x = createSupply(community);
        y = createSupply(community);
        z = createSupply(community);
        a = pendingAgreement(SharingAgreementStatus.PUBLISHED, x, y, z);
        activationService.setValidFrom(plant.getId(), a.id(), LocalDate.parse("2025-01-01"),
                List.of(a.of(x), a.of(y), a.of(z)));
        b = pendingAgreement(SharingAgreementStatus.PUBLISHED, x, y);
    }

    protected ResultActions perform(String authHeader, UUID plantId, UUID agreementId, String body) throws Exception {
        var request = post("/api/v1/plants/" + plantId + "/sharing-agreements/" + agreementId
                + "/partition-coefficients/" + operation())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (authHeader != null) {
            request.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        return mockMvc.perform(request).andDo(print());
    }

    protected String firstActivationBody() {
        return """
                {"coefficients": [{"coefficientId": "%s", "validFrom": "2025-03-01"},
                                  {"coefficientId": "%s", "validFrom": "2025-03-05"}],
                 "leavingClosures": []}
                """.formatted(b.of(x), b.of(y));
    }

    @Test
    void returnsUnauthorizedWithoutToken() throws Exception {
        perform(null, plant.getId(), b.id(), firstActivationBody())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsForbiddenForCommunityMember() throws Exception {
        perform(loginAsCommunityMember(community.getId()), plant.getId(), b.id(), firstActivationBody())
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForAnotherCommunitysAdmin() throws Exception {
        Community other = createCommunityRepository.create(CommunityMother.random().build());

        perform(loginAsCommunityAdmin(other.getId()), plant.getId(), b.id(), firstActivationBody())
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundWhenTheAgreementBelongsToAnotherPlantOfTheCommunity() throws Exception {
        Plant otherPlant = createPlant(community);

        perform(loginAsCommunityAdmin(community.getId()), otherPlant.getId(), b.id(), firstActivationBody())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SHA-015 a request whose lists are both empty is refused with 400")
    void returnsBadRequestWhenBothListsAreEmpty() throws Exception {
        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), b.id(),
                "{\"coefficients\": [], \"leavingClosures\": []}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenAListIsMissing() throws Exception {
        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), b.id(),
                "{\"coefficients\": [{\"coefficientId\": \"%s\", \"validFrom\": \"2025-03-01\"}]}".formatted(b.of(x)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SHA-015 a request listing a coefficient twice is refused with 400")
    void returnsBadRequestWhenACoefficientIsListedTwice() throws Exception {
        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), b.id(), """
                {"coefficients": [{"coefficientId": "%1$s", "validFrom": "2025-03-01"},
                                  {"coefficientId": "%1$s", "validFrom": "2025-03-05"}],
                 "leavingClosures": []}
                """.formatted(b.of(x)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SHA-015 a request listing a supply twice is refused with 400")
    void returnsBadRequestWhenASupplyIsListedTwice() throws Exception {
        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), b.id(), """
                {"coefficients": [],
                 "leavingClosures": [{"supplyId": "%1$s", "closedOn": "2025-03-01"},
                                     {"supplyId": "%1$s", "closedOn": "2025-03-05"}]}
                """.formatted(z.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConflictWhenTheAgreementIsDraft() throws Exception {
        Agreement draft = pendingAgreement(SharingAgreementStatus.DRAFT);

        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), draft.id(), """
                {"coefficients": [], "leavingClosures": [{"supplyId": "%s", "closedOn": "2025-03-01"}]}
                """.formatted(z.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_NOT_PUBLISHED"));
    }

    @Test
    @DisplayName("SHA-015 every invalid row is answered with 409 and its own error")
    void returnsConflictWithEveryInvalidRow() throws Exception {
        Supply withoutRows = createSupply(community);

        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), b.id(), """
                {"coefficients": [{"coefficientId": "%s", "validFrom": "2025-03-01"}],
                 "leavingClosures": [{"supplyId": "%s", "closedOn": "2025-03-01"},
                                     {"supplyId": "%s", "closedOn": "2025-03-01"}]}
                """.formatted(b.of(x), y.getId(), withoutRows.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_LEAVING_SUPPLY_IN_AGREEMENT"))
                .andExpect(jsonPath("$.errors[0].params.supplyId").value(y.getId().toString()))
                .andExpect(jsonPath("$.errors[0].params.cups").value(y.getCode()))
                .andExpect(jsonPath("$.errors[1].code").value("SHARING_AGREEMENT_NO_COEFFICIENT_TO_CLOSE"))
                .andExpect(jsonPath("$.errors[1].params.supplyId").value(withoutRows.getId().toString()));
    }

    /**
     * application-test.properties sets the zone to Europe/Madrid: local midnight of 2025-03-01 is 2025-02-28T23:00Z.
     */
    @Test
    @DisplayName("SHA-014 every effect of a first activation is reported")
    void returnsEveryEffectOfAFirstActivation() throws Exception {
        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), b.id(), firstActivationBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients", hasSize(2)))
                .andExpect(jsonPath("$.coefficients[0].coefficient.id").value(b.of(x).toString()))
                .andExpect(jsonPath("$.coefficients[0].supply.code").value(x.getCode()))
                .andExpect(jsonPath("$.coefficients[0].validFromBefore").isEmpty())
                .andExpect(jsonPath("$.coefficients[0].validFromAfter").value("2025-02-28T23:00:00Z"))
                .andExpect(jsonPath("$.coefficients[0].validToAfter").isEmpty())
                .andExpect(jsonPath("$.neighbours", hasSize(2)))
                .andExpect(jsonPath("$.neighbours[0].coefficient.id").value(a.of(x).toString()))
                .andExpect(jsonPath("$.neighbours[0].sharingAgreement.id").value(a.id().toString()))
                .andExpect(jsonPath("$.neighbours[0].validToAfter").value("2025-02-28T23:00:00Z"))
                .andExpect(jsonPath("$.leavingClosures", hasSize(1)))
                .andExpect(jsonPath("$.leavingClosures[0].supply.id").value(z.getId().toString()))
                .andExpect(jsonPath("$.leavingClosures[0].coefficient.id").value(a.of(z).toString()))
                .andExpect(jsonPath("$.leavingClosures[0].sharingAgreement.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.leavingClosures[0].validToBefore").isEmpty())
                .andExpect(jsonPath("$.leavingClosures[0].validToAfter").value("2025-02-28T23:00:00Z"))
                .andExpect(jsonPath("$.leavingClosures[0].source").value("DEFAULT"))
                .andExpect(jsonPath("$.agreementStatuses", hasSize(2)))
                .andExpect(jsonPath("$.agreementStatuses[?(@.sharingAgreement.id == '%s')].statusAfter"
                        .formatted(a.id())).value("SUPERSEDED"))
                .andExpect(jsonPath("$.communityStart.before").value("2024-12-31T23:00:00Z"))
                .andExpect(jsonPath("$.communityStart.after").value("2024-12-31T23:00:00Z"));
    }

    /**
     * C, created after B, holds X at 0 and Y. Dated on 2025-03-01 it comes right after A, where X holds 1, while B has
     * no start yet.
     */
    @Test
    @DisplayName("SHA-020 a coefficient that drops to 0 is reported as leaving the distribution")
    void reportsACoefficientThatDropsToZeroAsLeaving() throws Exception {
        Map<Supply, BigDecimal> inC = new LinkedHashMap<>();
        inC.put(x, BigDecimal.ZERO);
        inC.put(y, BigDecimal.ONE);
        Agreement c = pendingAgreement(SharingAgreementStatus.PUBLISHED, inC);
        String body = """
                {"coefficients": [{"coefficientId": "%s", "validFrom": "2025-03-01"},
                                  {"coefficientId": "%s", "validFrom": "2025-03-01"}],
                 "leavingClosures": []}
                """.formatted(c.of(x), c.of(y));

        perform(loginAsCommunityAdmin(community.getId()), plant.getId(), c.id(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients", hasSize(2)))
                .andExpect(jsonPath("$.coefficients[?(@.coefficient.id == '%s')].leavesDistribution"
                        .formatted(c.of(x))).value(contains(true)))
                .andExpect(jsonPath("$.coefficients[?(@.coefficient.id == '%s')].leavesDistribution"
                        .formatted(c.of(y))).value(contains(false)))
                .andExpect(jsonPath("$.leavingClosures", hasSize(1)))
                .andExpect(jsonPath("$.leavingClosures[0].supply.id").value(z.getId().toString()));
    }

    protected record Agreement(UUID id, Map<UUID, UUID> coefficientIdBySupplyId) {
        UUID of(Supply supply) {
            return coefficientIdBySupplyId.get(supply.getId());
        }
    }

    protected record Period(UUID id, Instant validFrom, Instant validTo) {
    }

    /** Every row of the plant, pending ones included, in a stable order. */
    protected List<Period> rows() {
        return getCoefficientRepository.findAllByPlantId(plant.getId()).stream()
                .sorted(Comparator.comparing(row -> row.getId().toString()))
                .map(row -> new Period(row.getId(), row.getValidFrom(), row.getValidTo()))
                .toList();
    }

    private Agreement pendingAgreement(SharingAgreementStatus status, Supply... supplies) {
        Map<Supply, BigDecimal> coefficients = new LinkedHashMap<>();
        Arrays.stream(supplies).forEach(supply -> coefficients.put(supply, BigDecimal.ONE));
        return pendingAgreement(status, coefficients);
    }

    private Agreement pendingAgreement(SharingAgreementStatus status, Map<Supply, BigDecimal> coefficients) {
        SharingAgreement agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .build());
        if (status == SharingAgreementStatus.PUBLISHED) {
            agreement = publishSharingAgreementRepository.publish(plant.getId(), agreement.getId());
        }
        Map<UUID, UUID> coefficientIdBySupplyId = new LinkedHashMap<>();
        for (Map.Entry<Supply, BigDecimal> entry : coefficients.entrySet()) {
            Supply supply = entry.getKey();
            SupplyPartitionCoefficient saved = saveCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                    .withId(UUID.randomUUID())
                    .withSupplyId(supply.getId())
                    .withPlantId(plant.getId())
                    .withSharingAgreementId(agreement.getId())
                    .withCoefficient(entry.getValue())
                    .withValidFrom(null)
                    .withValidTo(null)
                    .withCreatedAt(Instant.now())
                    .build());
            coefficientIdBySupplyId.put(supply.getId(), saved.getId());
        }
        return new Agreement(agreement.getId(), coefficientIdBySupplyId);
    }

    private Plant createPlant(Community owner) {
        Supply ownSupply = createSupply(owner);
        return createPlantRepository.create(PlantMother.random(ownSupply).build(), SupplyId.of(ownSupply.getId()));
    }

    private Supply createSupply(Community owner) {
        User user = UserMother.randomUser();
        userCreator.create(user);
        return createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()),
                owner.getId());
    }
}
