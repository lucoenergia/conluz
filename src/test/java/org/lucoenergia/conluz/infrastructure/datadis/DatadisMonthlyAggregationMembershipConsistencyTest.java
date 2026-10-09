package org.lucoenergia.conluz.infrastructure.datadis;

import com.fasterxml.jackson.databind.JsonNode;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.aggregate.DatadisMonthlyAggregationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindow;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate.DatadisMonthlyAggregationJob;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The symptom reported in #375, end to end: for a closed month, the membership monthly series (read
 * from the monthly pre-aggregate) and the membership energy metrics (summed from the hourly records
 * on request) must report the same figures once the daily monthly aggregation has run.
 *
 * <p>The month's points were aggregated from preliminary data -- no self-consumption and an
 * outdated grid import -- and the hourly records have since been revised. The job is built from the
 * application beans with a fixed {@link Clock} on 2026-10-09, so May 2026 is a closed month inside
 * the sync window.
 */
@Transactional
class DatadisMonthlyAggregationMembershipConsistencyTest extends BaseControllerTest {

    private static final String MONTHLY_PATH =
            "/api/v1/communities/{communityId}/memberships/{userId}/consumption/monthly";
    private static final String METRICS_PATH = "/api/v1/communities/{communityId}/memberships/{userId}/energy-metrics";

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T03:00:00Z"), ZoneOffset.UTC);
    private static final YearMonth MAY = YearMonth.of(2026, 5);
    private static final String MAY_START = "2026-05-01T00:00:00+02:00";
    private static final String MAY_END = "2026-05-31T23:00:00+02:00";

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture influxFixture;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    @Autowired
    private DatadisMonthlyAggregationService monthlyAggregationService;
    @Autowired
    private GetDatadisConfigurationService getDatadisConfigurationService;
    @Autowired
    private DatadisSyncWindow syncWindow;
    @Autowired
    private ZoneResolver zoneResolver;

    private final List<String> writtenCups = new ArrayList<>();

    @AfterEach
    void clearWrittenSeries() {
        writtenCups.forEach(cups -> {
            influxFixture.clear(cups);
            influxFixture.clearMonthlyAggregates(cups);
        });
    }

    @Test
    @DisplayName("DCA-001 after the daily run, the monthly series and the energy metrics agree on a closed month")
    void afterTheDailyRunTheMonthlySeriesAndTheEnergyMetricsAgreeOnAClosedMonth() throws Exception {
        CommunityEntity community = communityJpaRepository.save(CommunityMother.randomEntity().build());
        setDatadisConfigurationRepository.setDatadisConfiguration(community.getId(), new DatadisConfig.Builder()
                .setUsername("user").setPassword("password").setEnabled(true).build());
        User member = persistUser();
        createMembershipService.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        SupplyEntity first = persistSupply(member, community);
        SupplyEntity second = persistSupply(member, community);

        writeStaleMonth(first);
        writeStaleMonth(second);
        writeHour(first, "2026/05/01", "00:00", 10.5f, 4.25f, 1f);
        writeHour(first, "2026/05/31", "23:00", 1.25f, 0.5f, 0.25f);
        writeHour(second, "2026/05/15", "13:00", 20f, 8f, 2.5f);

        String token = loginUser(member);
        // The symptom: before the run, the series still holds the preliminary self-consumption.
        assertNotEquals(metrics(community, member, token).path("energy").path("selfConsumptionKWh").asDouble(),
                monthly(community, member, token).path("selfConsumptionEnergyKWh").asDouble());

        new DatadisMonthlyAggregationJob(monthlyAggregationService, CLOCK, getDatadisConfigurationService,
                syncWindow, zoneResolver).run();

        JsonNode month = monthly(community, member, token);
        JsonNode metrics = metrics(community, member, token);
        assertEquals(metrics.path("energy").path("selfConsumptionKWh").asDouble(),
                month.path("selfConsumptionEnergyKWh").asDouble(), 1e-9, "self-consumption");
        assertEquals(metrics.path("energy").path("surplusKWh").asDouble(),
                month.path("surplusEnergyKWh").asDouble(), 1e-9, "surplus");
        assertEquals(metrics.path("energy").path("gridImportKWh").asDouble(),
                month.path("consumptionKWh").asDouble(), 1e-9, "grid import");
        assertEquals(metrics.path("savings").path("amountEur").asDouble(),
                month.path("savingsEur").asDouble(), 1e-9, "savings");
        // And both are the sum of the revised hourly records.
        assertEquals(12.75, month.path("selfConsumptionEnergyKWh").asDouble(), 1e-9);
        assertEquals(31.75, month.path("consumptionKWh").asDouble(), 1e-9);
    }

    private JsonNode monthly(CommunityEntity community, User member, String token) throws Exception {
        JsonNode series = objectMapper.readTree(mockMvc.perform(get(MONTHLY_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", MAY_START)
                        .queryParam("endDate", MAY_END))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, series.size(), "one month requested");
        return series.get(0);
    }

    private JsonNode metrics(CommunityEntity community, User member, String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(METRICS_PATH, community.getId(), member.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .queryParam("startDate", MAY_START)
                        .queryParam("endDate", MAY_END))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private User persistUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private SupplyEntity persistSupply(User owner, CommunityEntity community) {
        SupplyEntity supply = supplyRepository.save(SupplyEntityMother.random(
                userRepository.getReferenceById(owner.getId()), community));
        writtenCups.add(supply.getCode());
        return supply;
    }

    private void writeHour(SupplyEntity supply, String date, String time, float gridImport, float selfConsumption,
                           float surplus) {
        DatadisConsumption record = DatadisConsumptionInfluxFixture.hourlyRecord(supply.getCode(), date, time,
                gridImport, selfConsumption, surplus);
        influxFixture.write(List.of(record));
    }

    /**
     * The point May had when it was aggregated from preliminary data.
     */
    private void writeStaleMonth(SupplyEntity supply) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();
            batchPoints.point(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT)
                    .time(MAY.atDay(1).atStartOfDay(ZONE).toInstant().toEpochMilli(), TimeUnit.MILLISECONDS)
                    .tag("cups", supply.getCode())
                    .addField("consumption_kwh", 90.0)
                    .addField("self_consumption_energy_kwh", 0.0)
                    .addField("surplus_energy_kwh", 3.0)
                    .addField("generation_energy_kwh", 0.0)
                    .addField("obtain_method", "Real")
                    .build());
            connection.write(batchPoints);
        }
    }
}
