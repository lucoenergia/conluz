package org.lucoenergia.conluz.infrastructure.production.plant.inverter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture.EnergyCommunity;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB. The clock is fixed at 2026-09-15T08:30 in Madrid, the day the
 * carry-over was observed.
 */
@Transactional
class GetPlantInverterProductionTodayControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/inverter-production/today";
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final double TOLERANCE = 1e-9;
    private static final Instant NOW = local("08:30");
    private static final double YESTERDAY_TOTAL = 323.36;

    @Autowired
    private CommunityEnergyTestFixture communityFixture;
    @Autowired
    private InverterProductionTestFixture fixture;

    @BeforeEach
    void givenAFixedClock() {
        travelTo(NOW);
    }

    @AfterEach
    void clearWrittenSnapshots() {
        fixture.clear();
    }

    @Test
    @DisplayName("PIP-001 AC1 PIP-002 AC4 the boundary snapshot is carry-over and today counts from 0")
    void theBoundarySnapshotIsCarryOverAndTodayCountsFromZero() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        Plant plant = community.north();
        fixture.configure(plant, true);
        Map<Instant, Double> snapshots = new LinkedHashMap<>();
        snapshots.put(local("00:00").minus(Duration.ofMinutes(5)), YESTERDAY_TOTAL);
        snapshots.put(local("00:00:01"), YESTERDAY_TOTAL);
        for (LocalTime time = LocalTime.of(0, 5); !time.isAfter(LocalTime.of(8, 0)); time = time.plusMinutes(5)) {
            snapshots.put(local(time.toString()), 0.0);
        }
        snapshots.put(local("08:05"), 0.04);
        snapshots.put(local("08:10"), 0.08);
        fixture.writeSnapshots(plant, snapshots);

        today(plant.getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.todayEnergyKWh").value(closeTo(0.08, TOLERANCE)))
                // 00:00:01 to 08:10 local: 99 snapshots, so 98 points.
                .andExpect(jsonPath("$.powerCurve", hasSize(98)))
                .andExpect(jsonPath("$.powerCurve[0].endsAt").value("2026-09-15T00:05:00+02:00"))
                .andExpect(jsonPath("$.powerCurve[0:96].powerKW", hasSize(96)))
                .andExpect(jsonPath("$.powerCurve[0:96].powerKW", everyItem(is(0.0))))
                .andExpect(jsonPath("$.powerCurve[96].endsAt").value("2026-09-15T08:05:00+02:00"))
                .andExpect(jsonPath("$.powerCurve[96].powerKW").value(closeTo(0.48, TOLERANCE)))
                .andExpect(jsonPath("$.powerCurve[97].powerKW").value(closeTo(0.48, TOLERANCE)))
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-09-15T08:10:00+02:00"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    @DisplayName("PIP-001 AC2 without a configuration the status is not available and every other field is null")
    void withoutAConfigurationTheStatusIsNotAvailable() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        fixture.writeSnapshot(community.north(), local("08:00"), 5.0);

        assertOnlyTheStatus(today(community.north().getId(), loginAsCommunityAdmin(community.id())),
                "NOT_AVAILABLE");
    }

    @Test
    @DisplayName("PIP-001 AC2 with a disabled configuration the status is not available, whatever is stored")
    void withADisabledConfigurationTheStatusIsNotAvailable() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        fixture.configure(community.north(), false);
        fixture.writeSnapshots(community.north(), Map.of(local("08:00"), 5.0, local("08:05"), 5.2));

        assertOnlyTheStatus(today(community.north().getId(), loginAsCommunityAdmin(community.id())),
                "NOT_AVAILABLE");
    }

    @Test
    @DisplayName("PIP-001 AC3 an enabled configuration whose latest snapshot is from yesterday has no data today")
    void anEnabledConfigurationWithOnlyYesterdaysSnapshotsHasNoDataToday() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        fixture.configure(community.north(), true);
        fixture.writeSnapshots(community.north(), Map.of(
                local("00:00").minus(Duration.ofMinutes(10)), 87.65,
                local("00:00").minus(Duration.ofMinutes(5)), 87.66));

        String body = assertOnlyTheStatus(today(community.north().getId(), loginAsCommunityAdmin(community.id())),
                "NO_DATA_TODAY").andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("87.6"), () -> "the body carries yesterday's value: " + body);
    }

    @Test
    @DisplayName("PIP-004 AC10 a latest snapshot 61 minutes old is stale, one 60 minutes old is not")
    void aLatestSnapshotOlderThanSixtyMinutesIsStale() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        fixture.configure(community.north(), true);
        fixture.configure(community.south(), true);
        fixture.writeSnapshots(community.north(), Map.of(local("07:20"), 1.0, local("07:29"), 1.1));
        fixture.writeSnapshots(community.south(), Map.of(local("07:25"), 1.0, local("07:30"), 1.1));
        String admin = loginAsCommunityAdmin(community.id());

        today(community.north().getId(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-09-15T07:29:00+02:00"))
                .andExpect(jsonPath("$.stale").value(true));
        today(community.south().getId(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-09-15T07:30:00+02:00"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    @DisplayName("PIP-003 AC8 a 20-minute interval is a null point, a 13 min 20 s one is averaged")
    void aTwentyMinuteIntervalIsANullPoint() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        fixture.configure(community.north(), true);
        fixture.writeSnapshots(community.north(), Map.of(
                local("06:41:41"), 2.0,
                local("06:55:01"), 2.4,
                local("07:15:01"), 3.4));

        today(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.powerCurve", hasSize(2)))
                .andExpect(jsonPath("$.powerCurve[0].powerKW").value(closeTo(1.8, TOLERANCE)))
                .andExpect(jsonPath("$.powerCurve[1].endsAt").value("2026-09-15T07:15:01+02:00"))
                .andExpect(jsonPath("$.powerCurve[1].powerKW").value(nullValue()));
    }

    @Test
    @DisplayName("PIP-003 another plant of the community, on its own station, never reaches the plant's curve")
    void anotherPlantsSnapshotsNeverReachThePlantsCurve() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        fixture.configure(community.north(), true);
        fixture.configure(community.south(), true);
        fixture.writeSnapshots(community.north(), Map.of(local("08:00"), 1.0, local("08:10"), 1.1));
        fixture.writeSnapshots(community.south(), Map.of(
                local("08:00").minus(Duration.ofHours(9)), 90.0,
                local("08:05"), 40.0,
                local("08:15"), 60.0));

        today(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayEnergyKWh").value(closeTo(1.1, TOLERANCE)))
                .andExpect(jsonPath("$.powerCurve", hasSize(1)))
                .andExpect(jsonPath("$.powerCurve[0].endsAt").value("2026-09-15T08:10:00+02:00"))
                .andExpect(jsonPath("$.powerCurve[0].powerKW").value(closeTo(0.6, TOLERANCE)))
                .andExpect(jsonPath("$.lastUpdatedAt").value("2026-09-15T08:10:00+02:00"));
    }

    @Test
    @DisplayName("EVL-001 today's energy and the power are rounded to 3 decimals, half-up")
    void todaysEnergyAndThePowerAreRounded() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        fixture.configure(community.north(), true);
        // 0.1 kWh over 301 s: 1.19601328... kW.
        fixture.writeSnapshots(community.north(), Map.of(local("08:00"), 12.2455, local("08:05:01"), 12.3455));

        today(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayEnergyKWh").value(12.346))
                .andExpect(jsonPath("$.powerCurve[0].powerKW").value(1.196));
    }

    @Test
    @DisplayName("PIP-005 AC11 a community admin reads it, a member gets 403, a non-member and a platform admin 404")
    void onlyACommunityAdminReadsIt() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        UUID plantId = community.north().getId();
        fixture.configure(community.north(), true);

        today(plantId, loginAsCommunityAdmin(community.id())).andExpect(status().isOk());
        // Member A owns the plant's own supply, and is still not an admin.
        today(plantId, loginUser(community.memberA())).andExpect(status().isForbidden());
        today(plantId, loginAsPartner()).andExpect(status().isNotFound());
        today(plantId, loginAsDefaultPlatformAdmin()).andExpect(status().isNotFound());
        today(plantId, loginUser(community.disabledMember())).andExpect(status().isNotFound());
        today(UUID.randomUUID(), loginAsCommunityAdmin(community.id())).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH, plantId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PIP-005 AC12 the serialised response carries no station code, provider code or credential")
    void theResponseCarriesNoStationCodeProviderCodeOrCredential() throws Exception {
        EnergyCommunity community = communityFixture.createCommunity();
        fixture.configure(community.north(), true);
        fixture.writeSnapshots(community.north(), Map.of(local("08:00"), 1.0, local("08:05"), 1.2));

        String body = today(community.north().getId(), loginAsCommunityAdmin(community.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        // The station the snapshots are stored under is the plant's provider code.
        for (Plant plant : new Plant[]{community.north(), community.south()}) {
            assertFalse(body.contains(plant.getProviderCode()), () -> "the body carries a provider code: " + body);
        }
        for (String credential : new String[]{"inverter-user", "inverter-password", "localhost:8000"}) {
            assertFalse(body.contains(credential), () -> "the body carries a credential: " + body);
        }
    }

    private ResultActions assertOnlyTheStatus(ResultActions result, String status) throws Exception {
        return result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$", hasKey("todayEnergyKWh")))
                .andExpect(jsonPath("$.todayEnergyKWh").value(nullValue()))
                .andExpect(jsonPath("$", hasKey("powerCurve")))
                .andExpect(jsonPath("$.powerCurve").value(nullValue()))
                .andExpect(jsonPath("$", hasKey("lastUpdatedAt")))
                .andExpect(jsonPath("$.lastUpdatedAt").value(nullValue()))
                .andExpect(jsonPath("$", hasKey("stale")))
                .andExpect(jsonPath("$.stale").value(nullValue()));
    }

    private ResultActions today(UUID plantId, String token) throws Exception {
        return mockMvc.perform(get(PATH, plantId).header(HttpHeaders.AUTHORIZATION, token));
    }

    private static Instant local(String time) {
        return LocalDateTime.of(2026, 9, 15, 0, 0).with(LocalTime.parse(time)).atZone(ZONE).toInstant();
    }
}
