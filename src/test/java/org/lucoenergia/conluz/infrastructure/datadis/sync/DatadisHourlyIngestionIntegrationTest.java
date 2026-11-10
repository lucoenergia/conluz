package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.GetSupplyEnergyMetricsService;
import org.lucoenergia.conluz.domain.consumption.SupplyConsumptionBucket;
import org.lucoenergia.conluz.domain.consumption.SupplyEnergyMetrics;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionService;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionPoint;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionMeasurements;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionPoint;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Datadis hourly records stored through the real synchronisation, persistence and InfluxDB, then read back by
 * the services that consume them. Only the Datadis API is replaced: it answers with records labelled as Datadis
 * labels them, with the local time each hour ends at. Every record carries a distinct consumption, so a record
 * counted in the wrong day or overwritten by another changes the sums asserted.
 */
@Transactional
class DatadisHourlyIngestionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GetDatadisConsumptionService getDatadisConsumptionService;
    @Autowired
    private GetSupplyEnergyMetricsService getSupplyEnergyMetricsService;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private InfluxDbConnectionManager influxDbConnectionManager;

    private final List<String> writtenCups = new ArrayList<>();

    @AfterEach
    void dropWrittenSeries() {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            for (String cups : writtenCups) {
                for (String measurement : List.of(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                        DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT)) {
                    connection.query(new Query(String.format("DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                            measurement, cups)));
                }
            }
        }
    }

    @Test
    @DisplayName("DHI-001 AC6 the daily series of the second of two synced days is the sum of exactly that day's 24 records")
    void theDailySeriesOfADayIsTheSumOfItsOwnRecords() {
        Community community = persistCommunity();
        Supply supply = persistSupply(community);
        List<DatadisConsumption> firstDay = day(supply.getCode(), "2025/11/03", hourLabels(), 100f);
        List<DatadisConsumption> secondDay = day(supply.getCode(), "2025/11/04", hourLabels(), 200f);
        List<DatadisConsumption> records = new ArrayList<>(firstDay);
        records.addAll(secondDay);
        doReturn(records).when(getDatadisConsumptionRepositoryDatadis)
                .getHourlyConsumptionsByMonth(any(DatadisConfig.class), anyString(), any(Supply.class), eq(Month.NOVEMBER), eq(2025));

        sync(community, supply, YearMonth.of(2025, 11));

        List<SupplyConsumptionBucket> buckets = getDatadisConsumptionService.getDailySeriesBySupply(
                SupplyId.of(supply.getId()),
                OffsetDateTime.parse("2025-11-04T00:00:00+01:00"),
                OffsetDateTime.parse("2025-11-04T23:59:59+01:00"));

        assertEquals(1, buckets.size(), () -> "buckets: " + buckets);
        assertEquals("2025/11/04", buckets.get(0).getConsumption().getDate());
        // 24 * 200 + (1 + ... + 24): neither the first day's 24:00 record nor any of its own is missing.
        assertEquals(sum(secondDay), buckets.get(0).getConsumption().getConsumptionKWh(), 1e-3);
    }

    @Test
    @DisplayName("DHI-001 AC7 a supply synced for October 2025 has data for every one of its 745 hours")
    void aMonthWithTheDayTheClocksGoBackIsFullyCovered() {
        Community community = persistCommunity();
        Supply supply = persistSupply(community);
        List<DatadisConsumption> october = new ArrayList<>();
        for (int dayOfMonth = 1; dayOfMonth <= 31; dayOfMonth++) {
            String date = String.format("2025/10/%02d", dayOfMonth);
            october.addAll(day(supply.getCode(), date, dayOfMonth == 26 ? fallBackLabels() : hourLabels(),
                    dayOfMonth * 100f));
        }
        assertEquals(745, october.size());
        doReturn(october).when(getDatadisConsumptionRepositoryDatadis)
                .getHourlyConsumptionsByMonth(any(DatadisConfig.class), anyString(), any(Supply.class), eq(Month.OCTOBER), eq(2025));

        sync(community, supply, YearMonth.of(2025, 10));

        SupplyEnergyMetrics metrics = getSupplyEnergyMetricsService.getEnergyMetrics(SupplyId.of(supply.getId()),
                OffsetDateTime.parse("2025-10-01T00:00:00+02:00"),
                OffsetDateTime.parse("2025-11-01T00:00:00+01:00"));

        assertEquals(745L, metrics.getExpectedHours());
        assertEquals(745L, metrics.getHoursWithData());
        assertEquals(sum(october), metrics.getGridImportKWh(), 1e-2);
    }

    @Test
    @DisplayName("DHI-003 AC5 a plant supply's hourly production lands in InfluxDB at the instants of its hourly consumption")
    void aPlantSuppliesProductionIsStoredAtTheInstantsOfItsConsumption() {
        Community community = persistCommunity();
        Supply supply = persistSupply(community);
        createPlantRepository.create(PlantMother.random().build(), SupplyId.of(supply.getId()));
        List<String> withoutTwoPm = hourLabels();
        withoutTwoPm.remove("14:00");
        List<DatadisConsumption> records = new ArrayList<>(day(supply.getCode(), "2025/10/25", hourLabels(), 0f));
        records.addAll(day(supply.getCode(), "2025/10/26", fallBackLabels(), 100f));
        records.addAll(day(supply.getCode(), "2025/10/27", withoutTwoPm, 200f));
        doReturn(records).when(getDatadisConsumptionRepositoryDatadis)
                .getHourlyConsumptionsByMonth(any(DatadisConfig.class), anyString(), any(Supply.class), eq(Month.OCTOBER), eq(2025));

        sync(community, supply, YearMonth.of(2025, 10));

        List<Instant> consumption = storedInstants(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT,
                DatadisConsumptionPoint.class, supply.getCode()).stream().map(DatadisConsumptionPoint::getTime).toList();
        List<Instant> production = storedInstants(DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT,
                DatadisProductionPoint.class, supply.getCode()).stream().map(DatadisProductionPoint::getTime).toList();

        assertEquals(24 + 25 + 23, consumption.size());
        assertEquals(consumption.size(), new HashSet<>(consumption).size());
        assertEquals(consumption, production);
        // The day the clocks go back runs from local 00:00 (+02:00) to local 23:00 (+01:00), one point per hour.
        for (int hour = 0; hour < 25; hour++) {
            assertEquals(Instant.parse("2025-10-25T22:00:00Z").plus(Duration.ofHours(hour)), production.get(24 + hour));
        }
    }

    private void sync(Community community, Supply supply, YearMonth month) {
        LocalDate day = month.atDay(1);
        datadisSyncService.synchronize(community.getId(), day, day, SupplyCode.of(supply.getCode()));
    }

    private <T> List<T> storedInstants(String measurement, Class<T> pointType, String cups) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            QueryResult result = connection.query(new Query(String.format(
                    "SELECT * FROM \"%s\" WHERE cups = '%s' ORDER BY time ASC", measurement, cups)));
            return new InfluxDBResultMapper().toPOJO(result, pointType);
        }
    }

    /**
     * A community with an enabled Datadis configuration, whose login the stubbed authorizer answers.
     */
    private Community persistCommunity() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        setDatadisConfigurationRepository.setDatadisConfiguration(community.getId(), new DatadisConfig.Builder()
                .setUsername("username")
                .setPassword("password")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(true)
                .build());
        doReturn("token").when(datadisAuthorizer).getAuthToken(any(DatadisConfig.class));
        return community;
    }

    private Supply persistSupply(Community community) {
        User owner = UserMother.randomUser();
        owner.enable();
        createUserRepository.create(owner);
        Supply supply = SupplyMother.random(owner)
                .withDistributor(new SupplyDistributor.Builder()
                        .withName("EDISTRIBUCION")
                        .withCode("2")
                        .withPointType(5)
                        .build())
                .build();
        Supply created = createSupplyRepository.create(supply, UserId.of(owner.getId()), community.getId());
        writtenCups.add(created.getCode());
        return created;
    }

    private static List<String> hourLabels() {
        List<String> labels = new ArrayList<>();
        for (int hour = 1; hour <= 24; hour++) {
            labels.add(String.format("%02d:00", hour));
        }
        return labels;
    }

    /** As Datadis labels the day the clocks go back: 03:00 twice. */
    private static List<String> fallBackLabels() {
        List<String> labels = hourLabels();
        labels.add(2, "03:00");
        return labels;
    }

    /** One record per label, in order, the n-th consuming {@code base + n} kWh. */
    private static List<DatadisConsumption> day(String cups, String date, List<String> labels, float base) {
        List<DatadisConsumption> records = new ArrayList<>();
        for (int position = 0; position < labels.size(); position++) {
            DatadisConsumption record = new DatadisConsumption();
            record.setCups(cups);
            record.setDate(date);
            record.setTime(labels.get(position));
            record.setConsumptionKWh(base + position + 1);
            record.setSelfConsumptionEnergyKWh(0f);
            record.setSurplusEnergyKWh((float) (position + 1));
            record.setGenerationEnergyKWh(0f);
            record.setObtainMethod("Real");
            records.add(record);
        }
        return records;
    }

    private static double sum(List<DatadisConsumption> records) {
        return records.stream().mapToDouble(DatadisConsumption::getConsumptionKWh).sum();
    }
}
