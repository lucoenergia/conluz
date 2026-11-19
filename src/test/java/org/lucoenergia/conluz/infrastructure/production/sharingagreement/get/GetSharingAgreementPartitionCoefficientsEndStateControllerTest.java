package org.lucoenergia.conluz.infrastructure.production.sharingagreement.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End states when agreements were created in an order other than the order of their periods, as after importing
 * historical agreements under a current one (#591). Periods start at local midnight in Europe/Madrid.
 */
@Transactional
class GetSharingAgreementPartitionCoefficientsEndStateControllerTest extends BaseControllerTest {

    private static final Instant Y2023 = Instant.parse("2022-12-31T23:00:00Z");
    private static final Instant Y2024 = Instant.parse("2023-12-31T23:00:00Z");
    private static final Instant Y2025 = Instant.parse("2024-12-31T23:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private UpdateSharingAgreementRepository updateSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveCoefficientRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private Community community;
    private Supply supplyX;
    private Supply supplyY;
    private Supply supplyZ;
    private int created;

    private void setUpCommunity() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        supplyX = persistSupply();
        supplyY = persistSupply();
        supplyZ = persistSupply();
    }

    private Supply persistSupply() {
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        return createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()),
                community.getId());
    }

    private Plant persistPlant() {
        Supply plantSupply = persistSupply();
        return createPlantRepository.create(PlantMother.random(plantSupply).build(), SupplyId.of(plantSupply.getId()));
    }

    // Each agreement is created one minute after the previous one, so the call order is the creation order.
    private SharingAgreement persistAgreement(Plant plant, String name, SharingAgreementStatus status) {
        return createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName(name)
                .withStatus(status)
                .withCreatedAt(CREATED.plusSeconds(60L * created++))
                .build());
    }

    private void persistCoefficient(Plant plant, SharingAgreement agreement, Supply supply, Instant validFrom,
                                    Instant validTo) {
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

    /**
     * The end state (and end date, when there is one) of each supply's row in the agreement, by supply.
     */
    private Map<UUID, String> endStates(Plant plant, SharingAgreement agreement) throws Exception {
        String body = mockMvc.perform(get("/api/v1/plants/" + plant.getId() + "/sharing-agreements/"
                        + agreement.getId() + "/partition-coefficients")
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<UUID, String> states = new HashMap<>();
        for (JsonNode row : objectMapper.readTree(body)) {
            String endDate = row.get("endDate").isNull() ? "" : " " + row.get("endDate").asText();
            states.put(UUID.fromString(row.get("supply").get("id").asText()),
                    row.get("endState").asText() + endDate);
        }
        return states;
    }

    @Test
    @DisplayName("SHA-016 (a) the current row of a supply in every agreement stays OPEN after historical agreements are imported")
    void currentRowStaysOpenAfterHistoricalImports() throws Exception {
        setUpCommunity();
        Plant plant = persistPlant();
        SharingAgreement c2025 = persistAgreement(plant, "C2025", SharingAgreementStatus.PUBLISHED);
        SharingAgreement a2023 = persistAgreement(plant, "A2023", SharingAgreementStatus.SUPERSEDED);
        SharingAgreement b2024 = persistAgreement(plant, "B2024", SharingAgreementStatus.SUPERSEDED);
        persistCoefficient(plant, a2023, supplyX, Y2023, Y2024);
        persistCoefficient(plant, b2024, supplyX, Y2024, Y2025);
        persistCoefficient(plant, c2025, supplyX, Y2025, null);

        assertEquals("OPEN", endStates(plant, c2025).get(supplyX.getId()));
    }

    @Test
    @DisplayName("SHA-016 (b) an open row is OPEN_ORPHAN when an agreement starting after it lacks its supply")
    void openRowIsOrphanWhenAnAgreementStartingAfterItLacksItsSupply() throws Exception {
        setUpCommunity();
        Plant plant = persistPlant();
        SharingAgreement c2025 = persistAgreement(plant, "C2025", SharingAgreementStatus.PUBLISHED);
        SharingAgreement a2023 = persistAgreement(plant, "A2023", SharingAgreementStatus.PUBLISHED);
        SharingAgreement b2024 = persistAgreement(plant, "B2024", SharingAgreementStatus.SUPERSEDED);
        persistCoefficient(plant, a2023, supplyX, Y2023, Y2024);
        persistCoefficient(plant, b2024, supplyX, Y2024, Y2025);
        persistCoefficient(plant, c2025, supplyX, Y2025, null);
        // Y's A2023 row is still open, so its C2025 row cannot be applied: the exclusion constraint refuses
        // the overlap.
        persistCoefficient(plant, a2023, supplyY, Y2023, null);
        persistCoefficient(plant, c2025, supplyY, null, null);

        assertEquals("OPEN_ORPHAN", endStates(plant, a2023).get(supplyY.getId()));
    }

    @ParameterizedTest(name = "D2026 created {0} the historical agreements")
    @ValueSource(strings = {"before", "after"})
    @DisplayName("SHA-016 (c) a pending row in a later published agreement is PENDING_SUCCESSION, whatever its creation time")
    void pendingRowOfALaterAgreementIsPendingSuccession(String when) throws Exception {
        setUpCommunity();
        Plant plant = persistPlant();
        SharingAgreement c2025 = persistAgreement(plant, "C2025", SharingAgreementStatus.PUBLISHED);
        SharingAgreement d2026 = "before".equals(when)
                ? persistAgreement(plant, "D2026", SharingAgreementStatus.PUBLISHED) : null;
        SharingAgreement a2023 = persistAgreement(plant, "A2023", SharingAgreementStatus.SUPERSEDED);
        SharingAgreement b2024 = persistAgreement(plant, "B2024", SharingAgreementStatus.SUPERSEDED);
        if (d2026 == null) {
            d2026 = persistAgreement(plant, "D2026", SharingAgreementStatus.PUBLISHED);
        }
        persistCoefficient(plant, a2023, supplyY, Y2023, Y2025);
        persistCoefficient(plant, b2024, supplyX, Y2024, Y2025);
        persistCoefficient(plant, c2025, supplyX, Y2025, null);
        persistCoefficient(plant, c2025, supplyY, Y2025, null);
        persistCoefficient(plant, d2026, supplyY, null, null);

        assertEquals("PENDING_SUCCESSION", endStates(plant, c2025).get(supplyY.getId()));
    }

    @Test
    @DisplayName("SHA-016 (AC1) an open row stays OPEN when later-created historical agreements, all placed before it, lack its supply")
    void openRowStaysOpenWhenHistoricalAgreementsLackItsSupply() throws Exception {
        setUpCommunity();
        Plant plant = persistPlant();
        SharingAgreement c2025 = persistAgreement(plant, "C2025", SharingAgreementStatus.PUBLISHED);
        SharingAgreement a2023 = persistAgreement(plant, "A2023", SharingAgreementStatus.SUPERSEDED);
        SharingAgreement b2024 = persistAgreement(plant, "B2024", SharingAgreementStatus.SUPERSEDED);
        persistCoefficient(plant, a2023, supplyX, Y2023, Y2024);
        persistCoefficient(plant, b2024, supplyX, Y2024, Y2025);
        persistCoefficient(plant, c2025, supplyX, Y2025, null);
        persistCoefficient(plant, c2025, supplyZ, Y2025, null);

        assertEquals("OPEN", endStates(plant, c2025).get(supplyZ.getId()));
    }

    @Test
    @DisplayName("SHA-008 an open row is not ended by a later-created agreement's row that starts before it")
    void openRowIsNotEndedByARowPlacedBeforeIt() throws Exception {
        setUpCommunity();
        Plant plant = persistPlant();
        SharingAgreement c2025 = persistAgreement(plant, "C2025", SharingAgreementStatus.PUBLISHED);
        SharingAgreement a2023 = persistAgreement(plant, "A2023", SharingAgreementStatus.SUPERSEDED);
        persistCoefficient(plant, a2023, supplyX, Y2023, Y2025);
        persistCoefficient(plant, c2025, supplyX, Y2025, null);

        assertEquals("OPEN", endStates(plant, c2025).get(supplyX.getId()));
    }

    @Test
    @DisplayName("SHA-016 an undated pending historical agreement registers last until its sent date places it before the current one")
    void undatedPendingHistoricalAgreementRegistersLast() throws Exception {
        setUpCommunity();
        Plant plant = persistPlant();
        SharingAgreement current = persistAgreement(plant, "C2025", SharingAgreementStatus.PUBLISHED);
        SharingAgreement historical = persistAgreement(plant, "H2024", SharingAgreementStatus.PUBLISHED);
        persistCoefficient(plant, current, supplyY, Y2025, null);
        persistCoefficient(plant, historical, supplyY, null, null);

        assertEquals("PENDING_SUCCESSION", endStates(plant, current).get(supplyY.getId()));

        updateSharingAgreementRepository.update(plant.getId(), historical.getId(), new UpdateSharingAgreement.Builder()
                .withName(historical.getName())
                .withDistributorFileSentOn(LocalDate.of(2024, 6, 1))
                .build());

        assertEquals("OPEN", endStates(plant, current).get(supplyY.getId()));
    }

    @Test
    @DisplayName("SHA-016 SHA-017 the same agreements created in two different orders have the same end states")
    void creationOrderChangesNoEndState() throws Exception {
        setUpCommunity();

        Map<String, Map<String, String>> chronological = reconstruction(List.of("A2023", "B2024", "C2025", "D2026"));
        Map<String, Map<String, String>> imported = reconstruction(List.of("C2025", "A2023", "B2024", "D2026"));

        assertEquals(chronological, imported);
        assertEquals(Map.of("X", "OPEN", "Y", "PENDING_SUCCESSION", "Z", "OPEN"), imported.get("C2025"));
        assertEquals(Map.of("Y", "OPEN"), imported.get("D2026"));
    }

    /**
     * One plant reconstructing the same history: X through A2023, B2024 and C2025; Y in A2023 and C2025, with a
     * pending successor in D2026; Z only in C2025. Agreements are created in {@code creationOrder}. Returns the end
     * states of every agreement, by agreement name and supply label.
     */
    private Map<String, Map<String, String>> reconstruction(List<String> creationOrder) throws Exception {
        Plant plant = persistPlant();
        Map<String, SharingAgreement> agreements = new HashMap<>();
        for (String name : creationOrder) {
            SharingAgreementStatus status = name.equals("A2023") || name.equals("B2024")
                    ? SharingAgreementStatus.SUPERSEDED : SharingAgreementStatus.PUBLISHED;
            agreements.put(name, persistAgreement(plant, name, status));
        }
        persistCoefficient(plant, agreements.get("A2023"), supplyX, Y2023, Y2024);
        persistCoefficient(plant, agreements.get("A2023"), supplyY, Y2023, Y2025);
        persistCoefficient(plant, agreements.get("B2024"), supplyX, Y2024, Y2025);
        persistCoefficient(plant, agreements.get("C2025"), supplyX, Y2025, null);
        persistCoefficient(plant, agreements.get("C2025"), supplyY, Y2025, null);
        persistCoefficient(plant, agreements.get("C2025"), supplyZ, Y2025, null);
        persistCoefficient(plant, agreements.get("D2026"), supplyY, null, null);

        Map<UUID, String> labels = Map.of(supplyX.getId(), "X", supplyY.getId(), "Y", supplyZ.getId(), "Z");
        Map<String, Map<String, String>> result = new TreeMap<>();
        for (Map.Entry<String, SharingAgreement> agreement : agreements.entrySet()) {
            Map<String, String> states = new TreeMap<>();
            endStates(plant, agreement.getValue()).forEach((supplyId, state) -> states.put(labels.get(supplyId), state));
            result.put(agreement.getKey(), states);
        }
        return result;
    }
}
