package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observed sharing on the plant data the Datadis sync really stores (#604): a plant supply with no
 * self-consumption at all, whose production was first stored as its surplus in the consumption measurement and
 * later as production, with that surplus stored as 0 from then on.
 *
 * <p>The clock stands at 2026-11-19, so the candidate months run from November 2024 to October 2026. The plant
 * has 100 kWp, so a valid day needs 50 kWh: every hour the fixtures write produces 5 kWh, 120 kWh a day.</p>
 */
class SharingObservationPlantSourcesControllerTest extends BaseControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final float HOURLY_PRODUCTION_KWH = 5f;

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture consumptionFixture;
    @Autowired
    private DatadisProductionInfluxFixture productionFixture;

    private ObservedSharingTestData data;
    private Community community;
    private Plant plant;
    private Supply consumer;

    @BeforeEach
    void setUp() {
        travelTo(NOW);
        data = new ObservedSharingTestData(createUserRepository, createCommunityRepository, createSupplyRepository,
                createPlantRepository, createSharingAgreementRepository,
                saveSupplyPartitionCoefficientRepository, consumptionFixture, productionFixture);
        community = data.community();
        plant = data.plant(community, 100);
        consumer = data.supply(community);
    }

    @AfterEach
    void tearDown() {
        data.clear();
    }

    /**
     * October holds its first 20 days of production only, 64 % of its hours: the range ends with September.
     */
    @Test
    @DisplayName("OBS-001 AC1 a plant supply with production and no consumption record is computable up to its latest month with 90 % of its hours")
    void aPlantWithProductionOnlyIsComputable() throws Exception {
        SharingAgreement agreement = data.agreement(plant, "Autumn", SharingAgreementStatus.PUBLISHED,
                LocalDate.parse("2026-08-20"));
        data.coefficient(consumer, plant, agreement, "1", LocalDate.parse("2026-09-01"), null);
        data.plantProducesWithoutConsumption(plant, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-20"),
                HOURLY_PRODUCTION_KWH);
        data.supplyReceives(consumer, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-20"),
                HOURLY_PRODUCTION_KWH);

        perform(agreementUrl(agreement.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.computable").value(true))
                .andExpect(jsonPath("$.analysis.analysedFrom").value("2026-09-01"))
                .andExpect(jsonPath("$.analysis.analysedTo").value("2026-09-30"))
                .andExpect(jsonPath("$.coefficients[0].status").value("APPLIED_SAME_DATE"))
                .andExpect(jsonPath("$.coefficients[0].observedValidDays").value(30));
    }

    @Test
    @DisplayName("OBS-001 AC2 the Luco-shaped plant is analysed from its first legacy surplus day to its latest published month")
    void aLucoShapedPlantIsAnalysedFromItsFirstLegacyDay() throws Exception {
        List<SharingAgreement> agreements = lucoShapedPlant();

        perform(plantUrl())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.computable").value(true))
                .andExpect(jsonPath("$.analysis.analysedFrom").value("2023-06-01"))
                .andExpect(jsonPath("$.analysis.analysedTo").value("2026-10-31"));
        // From the first production record on, G is production: the zeroed surplus would leave no valid day.
        perform(agreementUrl(agreements.get(1).getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[0].status").value("APPLIED_SAME_DATE"))
                .andExpect(jsonPath("$.coefficients[0].observedDate.from").value("2024-12-01"))
                .andExpect(jsonPath("$.coefficients[0].observedDate.to").value("2024-12-01"))
                .andExpect(jsonPath("$.coefficients[0].observedBeta").value(0.7))
                .andExpect(jsonPath("$.coefficients[0].observedValidDays").value(700));
    }

    /**
     * Every day of the 2024 agreement's window is before the first production record: it can only be observed
     * on the legacy surplus. The consumer's data starts on 1 February 2024, its first valid day, so the date is
     * open.
     */
    @Test
    @DisplayName("OBS-001 AC2 an agreement of 2024 is observed on the plant's legacy surplus")
    void anAgreementOf2024IsObservedOnTheLegacySurplus() throws Exception {
        List<SharingAgreement> agreements = lucoShapedPlant();

        perform(agreementUrl(agreements.get(0).getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[0].status").value("APPLIED_SAME_DATE"))
                .andExpect(jsonPath("$.coefficients[0].observedDate.from").value(nullValue()))
                .andExpect(jsonPath("$.coefficients[0].observedDate.to").value("2024-02-01"))
                .andExpect(jsonPath("$.coefficients[0].observedBeta").value(0.4))
                .andExpect(jsonPath("$.coefficients[0].observedValidDays").value(304));
    }

    @Test
    @DisplayName("OBS-001 the analysed range starts at the first surplus record, not at an earlier record without surplus")
    void theRangeStartsAtTheFirstSurplusRecord() throws Exception {
        data.plantConsumesWithoutSurplus(plant, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));
        data.plantHadLegacySurplus(plant, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"),
                HOURLY_PRODUCTION_KWH);

        perform(plantUrl())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.computable").value(true))
                .andExpect(jsonPath("$.analysis.analysedFrom").value("2026-09-01"))
                .andExpect(jsonPath("$.analysis.analysedTo").value("2026-09-30"));
    }

    /**
     * Three days of August's legacy surplus and three days of September's production hold 20 of their 24 hours,
     * below the daily threshold; September's zeroed surplus covers every hour, and must not make up for them.
     */
    @Test
    @DisplayName("OBS-001 AC3 a day whose own source holds fewer hours than the daily threshold is not plant-valid")
    void aDayBelowTheDailyThresholdOfItsSourceIsNotValid() throws Exception {
        SharingAgreement agreement = data.agreement(plant, "Summer", SharingAgreementStatus.PUBLISHED,
                LocalDate.parse("2026-07-20"));
        data.coefficient(consumer, plant, agreement, "1", LocalDate.parse("2026-08-01"), null);
        Set<LocalDate> shortDays = Set.of(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-11"),
                LocalDate.parse("2026-08-12"), LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-11"),
                LocalDate.parse("2026-09-12"));
        data.plantHadLegacySurplusAt(plant, withoutFourHoursOn(shortDays, "2026-08-01", "2026-08-31"),
                HOURLY_PRODUCTION_KWH);
        data.plantProducesAt(plant, withoutFourHoursOn(shortDays, "2026-09-01", "2026-09-30"),
                HOURLY_PRODUCTION_KWH);
        data.plantHadLegacySurplus(plant, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), 0f);
        data.supplyReceives(consumer, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-30"),
                HOURLY_PRODUCTION_KWH);

        perform(agreementUrl(agreement.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.analysedFrom").value("2026-08-01"))
                .andExpect(jsonPath("$.analysis.analysedTo").value("2026-09-30"))
                .andExpect(jsonPath("$.coefficients[0].observedValidDays").value(61 - 6));
    }

    @Test
    @DisplayName("OBS-006 AC4 a plant supply without any record in either source is not computable")
    void aPlantWithoutAnyRecordIsNotComputable() throws Exception {
        data.agreement(plant, "Autumn", SharingAgreementStatus.PUBLISHED, LocalDate.parse("2026-08-20"));
        data.supplyReceives(consumer, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"),
                HOURLY_PRODUCTION_KWH);

        perform(plantUrl())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.computable").value(false))
                .andExpect(jsonPath("$.analysis.notComputableReason").value("PLANT_SUPPLY_WITHOUT_DATA"))
                .andExpect(jsonPath("$.analysis.analysedFrom").value(nullValue()))
                .andExpect(jsonPath("$.analysis.suppliesWithValidDays").value(hasSize(0)));
    }

    @Test
    @DisplayName("OBS-006 a plant supply whose records reach no month of the last 24 with 90 % of its hours is not computable")
    void aPlantWithoutAPublishedMonthIsNotComputable() throws Exception {
        data.plantHadLegacySurplus(plant, LocalDate.parse("2024-01-01"), LocalDate.parse("2024-10-31"),
                HOURLY_PRODUCTION_KWH);
        data.plantProduces(plant, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-18"),
                HOURLY_PRODUCTION_KWH);

        perform(plantUrl())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.computable").value(false))
                .andExpect(jsonPath("$.analysis.notComputableReason").value("PLANT_SUPPLY_WITHOUT_DATA"))
                .andExpect(jsonPath("$.analysis.analysedFrom").value(nullValue()));
    }

    /**
     * Luco's shape: legacy surplus from 2023-06-01 to 2024-11-30, production from 2024-12-01 with the surplus
     * stored as 0 from then on, no self-consumption, and a consumer with data from 2024-02-01. The consumer
     * receives 40 % under the 2024 agreement and 70 % from the first production day on.
     */
    private List<SharingAgreement> lucoShapedPlant() {
        SharingAgreement of2024 = data.agreement(plant, "2024", SharingAgreementStatus.SUPERSEDED,
                LocalDate.parse("2024-01-20"));
        SharingAgreement winter = data.agreement(plant, "Winter 2024", SharingAgreementStatus.PUBLISHED,
                LocalDate.parse("2024-11-20"));
        LocalDate cutover = LocalDate.parse("2024-12-01");
        data.coefficient(consumer, plant, of2024, "0.4", LocalDate.parse("2024-02-01"), cutover);
        data.coefficient(consumer, plant, winter, "0.7", cutover, null);

        data.plantHadLegacySurplus(plant, LocalDate.parse("2023-06-01"), cutover.minusDays(1),
                HOURLY_PRODUCTION_KWH);
        data.plantProduces(plant, cutover, LocalDate.parse("2026-11-18"), HOURLY_PRODUCTION_KWH);
        data.supplyReceives(consumer, LocalDate.parse("2024-02-01"), cutover.minusDays(1),
                0.4f * HOURLY_PRODUCTION_KWH);
        data.supplyReceives(consumer, cutover, LocalDate.parse("2026-11-18"), 0.7f * HOURLY_PRODUCTION_KWH);
        return List.of(of2024, winter);
    }

    /**
     * Every hour of the local days {@code [first, last]}, except the last four of each day in {@code shortDays}.
     */
    private static List<Instant> withoutFourHoursOn(Set<LocalDate> shortDays, String first, String last) {
        return ObservedSharingTestData.hours(LocalDate.parse(first), LocalDate.parse(last)).stream()
                .filter(hour -> {
                    var local = hour.atZone(ObservedSharingTestData.ZONE);
                    return !(shortDays.contains(local.toLocalDate()) && local.getHour() >= 20);
                })
                .toList();
    }

    private ResultActions perform(String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())));
    }

    private String plantUrl() {
        return "/api/v1/plants/" + plant.getId() + "/sharing-agreements/observation";
    }

    private String agreementUrl(UUID sharingAgreementId) {
        return "/api/v1/plants/" + plant.getId() + "/sharing-agreements/" + sharingAgreementId + "/observation";
    }
}
