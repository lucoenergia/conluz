package org.lucoenergia.conluz.infrastructure.admin.supply.energytoday;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture.EnergyCommunity;
import org.lucoenergia.conluz.infrastructure.production.plant.inverter.InverterProductionTestFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-09-15T08:30 in Madrid. The supply under
 * test is member B's consumer supply; member A owns the plants' own supplies.
 */
@Transactional
class GetSupplyEnergyTodayControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies/{supplyId}/energy-today";
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final double TOLERANCE = 1e-9;
    private static final Instant NOW = local("08:30");
    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired
    private CommunityEnergyTestFixture communityFixture;
    @Autowired
    private InverterProductionTestFixture fixture;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;

    @BeforeEach
    void givenAFixedClock() {
        travelTo(NOW);
    }

    @AfterEach
    void clearWrittenSnapshots() {
        fixture.clear();
    }

    @Test
    @DisplayName("PIP-007 AC3 a coefficient that ended yesterday is not in force today: no coefficient today")
    void aCoefficientThatEndedYesterdayIsNotInForceToday() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        Supply supply = supplyOfB(community);
        holdCoefficient(community, supply, community.north(), LONG_AGO, local("00:00").minusSeconds(3600), "0.1");
        fixture.configure(community.north(), true);
        fixture.writeSnapshots(community.north(), Map.of(local("08:00"), 1.0, local("08:05"), 1.2));

        assertOnlyTheStatus(today(supply.getId(), loginUser(owner(community))), "NO_COEFFICIENT_TODAY");
    }

    @Test
    @DisplayName("PIP-007 AC4 a coefficient in a plant without an enabled configuration is not available")
    void aCoefficientInAPlantWithoutAnEnabledConfigurationIsNotAvailable() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        Supply supply = supplyOfB(community);
        holdCoefficient(community, supply, community.north(), LONG_AGO, null, "0.1");
        holdCoefficient(community, supply, community.south(), LONG_AGO, null, "0.2");
        fixture.configure(community.north(), false);
        fixture.writeSnapshots(community.north(), Map.of(local("08:00"), 1.0, local("08:05"), 1.2));

        assertOnlyTheStatus(today(supply.getId(), loginUser(owner(community))), "NOT_AVAILABLE");
    }

    @Test
    @DisplayName("PIP-007 AC5 an enabled configuration with only yesterday's snapshots has no data today")
    void anEnabledConfigurationWithOnlyYesterdaysSnapshotsHasNoDataToday() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        Supply supply = supplyOfB(community);
        holdCoefficient(community, supply, community.north(), LONG_AGO, null, "0.1");
        holdCoefficient(community, supply, community.south(), LONG_AGO, null, "0.2");
        fixture.configure(community.north(), true);
        fixture.configure(community.south(), false);
        fixture.writeSnapshots(community.north(), Map.of(
                local("00:00").minusSeconds(600), 87.65, local("00:00").minusSeconds(300), 87.66));
        fixture.writeSnapshots(community.south(), Map.of(local("08:00"), 1.0, local("08:05"), 1.2));

        assertOnlyTheStatus(today(supply.getId(), loginUser(owner(community))), "NO_DATA_TODAY");
    }

    @Test
    @DisplayName("PIP-007 AC6 PIP-008 PIP-009 PIP-010 PIP-004 two plants, β changing mid-day, summed into fixed slots")
    void twoPlantsWithAMidDayChangeAreSummedIntoFixedSlots() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        Supply supply = supplyOfB(community);
        holdCoefficient(community, supply, community.north(), LONG_AGO, local("08:07"), "0.1");
        holdCoefficient(community, supply, community.north(), local("08:07"), null, "0.3");
        holdCoefficient(community, supply, community.south(), LONG_AGO, null, "0.2");
        fixture.configure(community.north(), true);
        fixture.configure(community.south(), true);
        fixture.writeSnapshots(community.north(), Map.of(
                local("08:00"), 1.0, local("08:05"), 1.5, local("08:10"), 2.1, local("08:15"), 2.4));
        fixture.writeSnapshots(community.south(), Map.of(
                local("08:00"), 2.0, local("08:05"), 2.3, local("08:10"), 3.0, local("08:15"), 3.2));

        today(supply.getId(), loginUser(owner(community)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production.status").value("AVAILABLE"))
                // North: 1.0 × 0.1 up to its first snapshot, 0.5 × 0.1, 0.6 × 0.1 (starts at 08:05, before the
                // change), 0.3 × 0.3. South: 3.2 × 0.2.
                .andExpect(jsonPath("$.production.assignedEnergyKWh").value(closeTo(0.94, TOLERANCE)))
                // 00:00 to 08:15: 99 slots. Up to 08:00 the interval from midnight is a gap.
                .andExpect(jsonPath("$.production.assignedPowerCurve", hasSize(99)))
                .andExpect(jsonPath("$.production.assignedPowerCurve[0:96].powerKW", everyItem(nullValue())))
                .andExpect(jsonPath("$.production.assignedPowerCurve[96].endsAt")
                        .value("2026-09-15T08:05:00+02:00"))
                .andExpect(jsonPath("$.production.assignedPowerCurve[96].powerKW").value(closeTo(1.32, TOLERANCE)))
                .andExpect(jsonPath("$.production.assignedPowerCurve[97].powerKW").value(closeTo(2.4, TOLERANCE)))
                .andExpect(jsonPath("$.production.assignedPowerCurve[98].endsAt")
                        .value("2026-09-15T08:15:00+02:00"))
                .andExpect(jsonPath("$.production.assignedPowerCurve[98].powerKW").value(closeTo(1.56, TOLERANCE)))
                .andExpect(jsonPath("$.production.currentAssignedPowerKW").value(closeTo(1.56, TOLERANCE)))
                .andExpect(jsonPath("$.production.currentPlantPowerKW").value(closeTo(6.0, TOLERANCE)))
                .andExpect(jsonPath("$.production.partitionCoefficient").value(nullValue()))
                .andExpect(jsonPath("$.production.lastUpdatedAt").value("2026-09-15T08:15:00+02:00"))
                // Exactly 15 minutes old.
                .andExpect(jsonPath("$.production.stale").value(false));
    }

    @Test
    @DisplayName("PIP-010 PIP-004 AC9 AC11 with a single plant, its β is reported, and 16 minutes old is stale")
    void withASinglePlantItsCoefficientIsReported() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        Supply supply = supplyOfB(community);
        holdCoefficient(community, supply, community.south(), LONG_AGO, null, "0.2");
        fixture.configure(community.south(), true);
        fixture.writeSnapshots(community.south(), Map.of(local("08:04"), 1.0, local("08:09"), 1.2,
                local("08:14"), 1.5));

        today(supply.getId(), loginUser(owner(community)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.production.assignedEnergyKWh").value(closeTo(0.3, TOLERANCE)))
                .andExpect(jsonPath("$.production.partitionCoefficient").value(closeTo(0.2, TOLERANCE)))
                .andExpect(jsonPath("$.production.lastUpdatedAt").value("2026-09-15T08:14:00+02:00"))
                .andExpect(jsonPath("$.production.stale").value(true));
    }

    @Test
    @DisplayName("PIP-008 AC12 the energy is rounded to 3 decimals once, after summing, never per interval")
    void theEnergyIsRoundedOnceAfterSumming() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        Supply supply = supplyOfB(community);
        holdCoefficient(community, supply, community.south(), LONG_AGO, null, "0.1");
        fixture.configure(community.south(), true);
        Map<Instant, Double> snapshots = new LinkedHashMap<>();
        for (int minute = 0; minute <= 10; minute++) {
            snapshots.put(local("08:00").plusSeconds(60L * minute), 0.004 * minute);
        }
        fixture.writeSnapshots(community.south(), snapshots);

        // Ten intervals of 0.004 kWh × 0.1 = 0.0004 each: rounded one by one they would sum to 0.
        today(supply.getId(), loginUser(owner(community)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.production.assignedEnergyKWh").value(closeTo(0.004, TOLERANCE)));
    }

    @Test
    @DisplayName("PIP-011 AC13 AC14 AC15 the owner and a community admin read it, everyone else gets 404")
    void theOwnerAndACommunityAdminReadIt() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        EnergyCommunity other = communityFixture.createCommunity();
        UUID supplyId = supplyOfB(community).getId();

        today(supplyId, loginUser(owner(community))).andExpect(status().isOk());
        today(supplyId, loginAsCommunityAdmin(community.id())).andExpect(status().isOk());
        // Member A, of the same community, owns the plants' supplies but not this one.
        today(supplyId, loginUser(community.memberA())).andExpect(status().isNotFound());
        today(supplyId, loginUser(other.memberA())).andExpect(status().isNotFound());
        today(supplyId, loginAsDefaultPlatformAdmin()).andExpect(status().isNotFound());
        // Member C owns a supply but their membership is disabled.
        today(community.supplies().get(2).getId(), loginUser(community.disabledMember()))
                .andExpect(status().isNotFound());
        today(UUID.randomUUID(), loginAsCommunityAdmin(community.id())).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH, supplyId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PIP-006 AC1 every status answers every field of the contract, and no plant is identified")
    void everyStatusAnswersEveryFieldOfTheContract() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        Supply supply = supplyOfB(community);
        String owner = loginUser(owner(community));
        List<String> required = requiredOf("SupplyProductionTodayResponse");
        List<String> pointRequired = requiredOf("SupplyAssignedPowerPointResponse");

        String noCoefficient = today(supply.getId(), owner).andReturn().getResponse().getContentAsString();
        holdCoefficient(community, supply, community.north(), LONG_AGO, null, "0.1");
        String notAvailable = today(supply.getId(), owner).andReturn().getResponse().getContentAsString();
        fixture.configure(community.north(), true);
        String noData = today(supply.getId(), owner).andReturn().getResponse().getContentAsString();
        fixture.writeSnapshots(community.north(), Map.of(local("08:20"), 1.0, local("08:25"), 1.2));
        String available = today(supply.getId(), owner).andReturn().getResponse().getContentAsString();

        for (String body : List.of(noCoefficient, notAvailable, noData, available)) {
            JsonNode response = objectMapper.readTree(body);
            assertEquals(List.of("production"), fieldNames(response), body);
            assertEquals(required, fieldNames(response.path("production")), body);
            response.path("production").path("assignedPowerCurve")
                    .forEach(point -> assertEquals(pointRequired, fieldNames(point), body));
            for (Plant plant : List.of(community.north(), community.south())) {
                for (String identifier : List.of(plant.getId().toString(), plant.getName(),
                        plant.getProviderCode())) {
                    assertFalse(body.contains(identifier), () -> "the body identifies a plant: " + body);
                }
            }
        }
        assertEquals("AVAILABLE", objectMapper.readTree(available).path("production").path("status").asText());
    }

    private void holdCoefficient(EnergyCommunity community, Supply supply, Plant plant, Instant validFrom,
                                 Instant validTo, String coefficient) {
        SharingAgreement agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.PUBLISHED)
                .withNominalPowerKW(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .withCreatedBy(community.memberA().getId())
                .build());
        saveSupplyPartitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supply.getId())
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(new BigDecimal(coefficient))
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }

    private static Supply supplyOfB(EnergyCommunity community) {
        return community.supplies().get(1);
    }

    private static User owner(EnergyCommunity community) {
        return community.members().get(1);
    }

    private ResultActions assertOnlyTheStatus(ResultActions result, String status) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.production.status").value(status));
        for (String field : List.of("assignedPowerCurve", "assignedEnergyKWh", "currentAssignedPowerKW",
                "currentPlantPowerKW", "partitionCoefficient", "lastUpdatedAt", "stale")) {
            result.andExpect(jsonPath("$.production." + field).value(nullValue()));
        }
        return result;
    }

    private ResultActions today(UUID supplyId, String token) throws Exception {
        return mockMvc.perform(get(PATH, supplyId).header(HttpHeaders.AUTHORIZATION, token));
    }

    private List<String> requiredOf(String schema) throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> required = new ArrayList<>();
        document.path("components").path("schemas").path(schema).path("required")
                .forEach(field -> required.add(field.asText()));
        assertFalse(required.isEmpty(), () -> "no required fields in " + schema);
        return required.stream().sorted().toList();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names.stream().sorted().toList();
    }

    private static Instant local(String time) {
        return LocalDateTime.of(2026, 9, 15, 0, 0).with(LocalTime.parse(time)).atZone(ZONE).toInstant();
    }
}
