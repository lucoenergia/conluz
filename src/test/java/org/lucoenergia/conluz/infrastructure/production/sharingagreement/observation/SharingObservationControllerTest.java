package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.production.datadis.DatadisProductionInfluxFixture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two observed sharing operations end to end, over PostgreSQL and InfluxDB: a small plant whose supplies
 * move from agreement A to agreement B on 1 November 2025, one of them leaving the distribution.
 *
 * <p>The application clock starts on 2026-01-01, so the latest month published for the plant supply is December
 * 2025. Not {@code @Transactional}: a session kept open through the request would hide a lazy load.</p>
 */
class SharingObservationControllerTest extends BaseControllerTest {

    private static final LocalDate FIRST_DAY = LocalDate.parse("2025-09-01");
    private static final LocalDate CHANGE = LocalDate.parse("2025-11-01");
    private static final LocalDate LAST_DAY = LocalDate.parse("2025-12-31");
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
    private GetSharingAgreementRepository getSharingAgreementRepository;
    @Autowired
    private GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository;
    @Autowired
    private DatadisConsumptionInfluxFixture consumptionFixture;
    @Autowired
    private DatadisProductionInfluxFixture productionFixture;

    private ObservedSharingTestData data;
    private Community community;
    private Community otherCommunity;
    private Plant plant;
    private Plant otherPlantOfTheCommunity;
    private Supply staying;
    private Supply leaving;
    private Supply silent;
    private SharingAgreement a;
    private SharingAgreement b;
    private SharingAgreement draft;
    private SharingAgreement agreementOfTheOtherPlant;

    @BeforeEach
    void setUp() {
        data = new ObservedSharingTestData(createUserRepository, createCommunityRepository, createSupplyRepository,
                createPlantRepository, createSharingAgreementRepository,
                saveSupplyPartitionCoefficientRepository, consumptionFixture, productionFixture);
        community = data.community();
        otherCommunity = data.community();
        plant = data.plant(community, 100);
        otherPlantOfTheCommunity = data.plant(community, 100);
        staying = data.supply(community);
        leaving = data.supply(community);
        silent = data.supply(community);

        a = data.agreement(plant, "A", SharingAgreementStatus.SUPERSEDED, LocalDate.parse("2025-08-20"));
        b = data.agreement(plant, "B", SharingAgreementStatus.PUBLISHED, LocalDate.parse("2025-10-25"));
        draft = data.agreement(plant, "Draft", SharingAgreementStatus.DRAFT, null);
        agreementOfTheOtherPlant = data.agreement(otherPlantOfTheCommunity, "Other", SharingAgreementStatus.PUBLISHED,
                null);
        data.coefficient(staying, plant, a, "0.6", LocalDate.parse("2025-08-25"), CHANGE);
        data.coefficient(leaving, plant, a, "0.4", LocalDate.parse("2025-08-25"), CHANGE);
        data.coefficient(staying, plant, b, "1", CHANGE, null);
        data.coefficient(staying, plant, draft, "0.5", null, null);
        data.coefficient(leaving, plant, draft, "0.5", null, null);

        data.plantProduces(plant, FIRST_DAY, LAST_DAY, HOURLY_PRODUCTION_KWH);
        data.supplyReceives(staying, FIRST_DAY, CHANGE.minusDays(1), 0.6f * HOURLY_PRODUCTION_KWH);
        data.supplyReceives(staying, CHANGE, LAST_DAY, HOURLY_PRODUCTION_KWH);
        data.supplyReceives(leaving, FIRST_DAY, CHANGE.minusDays(1), 0.4f * HOURLY_PRODUCTION_KWH);
        data.supplyReceives(leaving, CHANGE, LAST_DAY, 0f);
    }

    @AfterEach
    void tearDown() {
        data.clear();
    }

    @Test
    @DisplayName("OBS-007 the plant's observed sharing reports each agreement in scope, end to end")
    void plantObservation() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        perform(plantUrl(plant.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consecutiveDays").value(3))
                .andExpect(jsonPath("$.analysis.computable").value(true))
                .andExpect(jsonPath("$.analysis.notComputableReason").value(nullValue()))
                .andExpect(jsonPath("$.analysis.analysedFrom").value("2025-09-01"))
                .andExpect(jsonPath("$.analysis.analysedTo").value("2025-12-31"))
                .andExpect(jsonPath("$.analysis.suppliesWithValidDays[*].id")
                        .value(containsInAnyOrder(staying.getId().toString(), leaving.getId().toString())))
                .andExpect(jsonPath("$.analysis.suppliesWithoutValidDays[*].id").value(
                        containsInAnyOrder(silent.getId().toString())))
                .andExpect(jsonPath("$.analysis.notComputableSupplies").value(hasSize(0)))
                // The draft is not analysed; the plant supplies of the community are not consumers.
                .andExpect(jsonPath("$.sharingAgreements[*].sharingAgreement.name").value(
                        org.hamcrest.Matchers.contains("A", "B")))
                .andExpect(jsonPath("$.sharingAgreements[0].distributorFileSentOn").value("2025-08-20"))
                .andExpect(jsonPath("$.sharingAgreements[0].mostFrequentObservedDate.from").value(nullValue()))
                .andExpect(jsonPath("$.sharingAgreements[0].mostFrequentObservedDate.to").value("2025-09-01"))
                .andExpect(jsonPath("$.sharingAgreements[0].coefficientStatusCounts.appliedSameDate").value(2))
                .andExpect(jsonPath("$.sharingAgreements[0].recommendations").value(
                        org.hamcrest.Matchers.contains("NONE")))
                .andExpect(jsonPath("$.sharingAgreements[1].mostFrequentObservedDate.from").value("2025-11-01"))
                .andExpect(jsonPath("$.sharingAgreements[1].mostFrequentObservedDate.to").value("2025-11-01"))
                .andExpect(jsonPath("$.sharingAgreements[1].coefficientStatusCounts.appliedSameDate").value(1))
                .andExpect(jsonPath("$.sharingAgreements[1].leavingStatusCounts.appliedSameDate").value(1))
                .andExpect(jsonPath("$.sharingAgreements[1].anomalies").value(hasSize(0)))
                .andExpect(jsonPath("$.sharingAgreements[1].unallocatedShare.days").value(61))
                .andExpect(jsonPath("$.sharingAgreements[1].recommendations").value(
                        org.hamcrest.Matchers.contains("NONE")))
                .andExpect(jsonPath("$.unattributedAnomalies").value(hasSize(0)));
    }

    @Test
    @DisplayName("OBS-008 the agreement's observed sharing lists its rows and the supply leaving in it")
    void agreementObservation() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.sharingAgreement.id").value(b.getId().toString()))
                .andExpect(jsonPath("$.coefficients").value(hasSize(1)))
                .andExpect(jsonPath("$.coefficients[0].supply.id").value(staying.getId().toString()))
                .andExpect(jsonPath("$.coefficients[0].storedValidFrom").value("2025-11-01"))
                .andExpect(jsonPath("$.coefficients[0].status").value("APPLIED_SAME_DATE"))
                .andExpect(jsonPath("$.coefficients[0].observedDate.to").value("2025-11-01"))
                .andExpect(jsonPath("$.leavingRows").value(hasSize(1)))
                .andExpect(jsonPath("$.leavingRows[0].supply.id").value(leaving.getId().toString()))
                .andExpect(jsonPath("$.leavingRows[0].previousSharingAgreement.id").value(a.getId().toString()))
                .andExpect(jsonPath("$.leavingRows[0].previousCoefficient").value(0.4))
                .andExpect(jsonPath("$.leavingRows[0].observedLeavingDate.from").value("2025-11-01"))
                .andExpect(jsonPath("$.leavingRows[0].observedLeavingDate.to").value("2025-11-01"))
                .andExpect(jsonPath("$.leavingRows[0].storedValidTo").value("2025-11-01"))
                .andExpect(jsonPath("$.leavingRows[0].status").value("APPLIED_SAME_DATE"));
    }

    @Test
    @DisplayName("OBS-007 the agreement's observed sharing is exactly its slice of the plant's")
    void agreementIsTheSliceOfThePlant() throws Exception {
        // An anomaly attributed to B, so the slice carries its observed β too.
        supplyWithAnUnknownCoefficientInB();
        String admin = loginAsCommunityAdmin(community.getId());

        JsonNode plantResult = read(perform(plantUrl(plant.getId()), admin).andExpect(status().isOk()));
        for (SharingAgreement agreement : List.of(a, b)) {
            JsonNode agreementResult = read(perform(agreementUrl(plant.getId(), agreement.getId()), admin)
                    .andExpect(status().isOk()));
            JsonNode item = null;
            for (JsonNode candidate : plantResult.get("sharingAgreements")) {
                if (candidate.get("sharingAgreement").get("id").asText().equals(agreement.getId().toString())) {
                    item = candidate;
                }
            }
            assertThat(agreementResult.get("summary")).isEqualTo(item);
            assertThat(agreementResult.get("analysis")).isEqualTo(plantResult.get("analysis"));
            assertThat(agreementResult.get("consecutiveDays")).isEqualTo(plantResult.get("consecutiveDays"));
            assertThat(agreementResult.get("coefficients").size()).isEqualTo(sum(item.get("coefficientStatusCounts")));
            assertThat(agreementResult.get("leavingRows").size()).isEqualTo(sum(item.get("leavingStatusCounts")));
        }
    }

    @Test
    @DisplayName("OBS-009 the agreement's rows report the median of their supply's daily β over its applied window")
    void rowsReportTheirObservedBeta() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        // B's applied window is November and December: 61 valid days.
        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[0].observedBeta").value(1.0))
                .andExpect(jsonPath("$.coefficients[0].observedValidDays").value(61))
                .andExpect(jsonPath("$.leavingRows[0].observedBeta").value(0.0))
                .andExpect(jsonPath("$.leavingRows[0].observedValidDays").value(61));
        // The silent supply has no valid day in A's window.
        data.coefficient(silent, plant, a, "0.1", LocalDate.parse("2025-08-25"), CHANGE);
        perform(agreementUrl(plant.getId(), a.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + staying.getId() + "')].observedBeta")
                        .value(contains(0.6)))
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + staying.getId() + "')].observedValidDays")
                        .value(contains(61)))
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + silent.getId() + "')].observedBeta")
                        .value(contains(nullValue())))
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + silent.getId() + "')].observedValidDays")
                        .value(contains(0)));
    }

    @Test
    @DisplayName("OBS-009 the plant's agreement summaries report the observed β of their anomalies")
    void plantSummariesReportTheObservedBetaOfAnomalies() throws Exception {
        Supply deviating = supplyWithAnUnknownCoefficientInB();
        String admin = loginAsCommunityAdmin(community.getId());

        perform(plantUrl(plant.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharingAgreements[1].anomalies").value(hasSize(1)))
                .andExpect(jsonPath("$.sharingAgreements[1].anomalies[0].supply.id").value(deviating.getId().toString()))
                .andExpect(jsonPath("$.sharingAgreements[1].anomalies[0].type").value("UNKNOWN_COEFFICIENT"))
                .andExpect(jsonPath("$.sharingAgreements[1].anomalies[0].observedBeta").value(0.0412))
                .andExpect(jsonPath("$.sharingAgreements[1].anomalies[0].observedValidDays").value(5));
    }

    @Test
    @DisplayName("OBS-012 the agreement's observed sharing reports the expected β of an anomaly attributed to it")
    void agreementReportsTheExpectedBetaOfItsAnomalies() throws Exception {
        Supply deviating = supplyWithAnUnknownCoefficientInB();
        String admin = loginAsCommunityAdmin(community.getId());

        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.anomalies").value(hasSize(1)))
                .andExpect(jsonPath("$.summary.anomalies[0].supply.id").value(deviating.getId().toString()))
                .andExpect(jsonPath("$.summary.anomalies[0].type").value("UNKNOWN_COEFFICIENT"))
                .andExpect(jsonPath("$.summary.anomalies[0].expectedBeta").value(0.06))
                .andExpect(jsonPath("$.summary.anomalies[0].observedBeta").value(0.0412));
    }

    @Test
    @DisplayName("OBS-012 the plant's observed sharing reports a null expected β on an anomaly attributed to no agreement")
    void plantReportsNoExpectedBetaOnUnattributedAnomalies() throws Exception {
        // In no agreement, the silent supply receives energy for five days.
        data.supplyReceives(silent, LocalDate.parse("2025-12-01"), LocalDate.parse("2025-12-05"),
                0.02f * HOURLY_PRODUCTION_KWH);
        String admin = loginAsCommunityAdmin(community.getId());

        JsonNode result = read(perform(plantUrl(plant.getId()), admin).andExpect(status().isOk()));

        assertThat(result.get("unattributedAnomalies")).hasSize(1);
        JsonNode anomaly = result.get("unattributedAnomalies").get(0);
        assertThat(anomaly.get("supply").get("id").asText()).isEqualTo(silent.getId().toString());
        assertThat(anomaly.get("type").asText()).isEqualTo("ENERGY_OUTSIDE_AGREEMENT");
        assertThat(anomaly.has("expectedBeta")).isTrue();
        assertThat(anomaly.get("expectedBeta").isNull()).isTrue();
    }

    @Test
    @DisplayName("OBS-013 the agreement's rows report the plant-valid days of their row window next to their supply's valid days")
    void rowsReportTheirWindowDays() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        // B's applied window is November and December: 61 plant-valid days, all of them valid for both supplies.
        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[0].windowDays").value(61))
                .andExpect(jsonPath("$.coefficients[0].observedValidDays").value(61))
                .andExpect(jsonPath("$.leavingRows[0].windowDays").value(61))
                .andExpect(jsonPath("$.leavingRows[0].observedValidDays").value(61));
        // The silent supply has no valid day in A's window, which still holds A's plant-valid days.
        data.coefficient(silent, plant, a, "0.1", LocalDate.parse("2025-08-25"), CHANGE);
        perform(agreementUrl(plant.getId(), a.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + silent.getId() + "')].windowDays")
                        .value(contains(61)))
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + silent.getId() + "')].observedValidDays")
                        .value(contains(0)));
    }

    @Test
    @DisplayName("OBS-014 both operations report the first and last local day of each agreement's applied window")
    void bothOperationsReportTheAppliedWindow() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        // A is applied from the open date of the first valid day until the day before B, which runs to the end of
        // the analysed range.
        perform(plantUrl(plant.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharingAgreements[0].unallocatedShare.from").value("2025-09-01"))
                .andExpect(jsonPath("$.sharingAgreements[0].unallocatedShare.to").value("2025-10-31"))
                .andExpect(jsonPath("$.sharingAgreements[1].unallocatedShare.from").value("2025-11-01"))
                .andExpect(jsonPath("$.sharingAgreements[1].unallocatedShare.to").value("2025-12-31"));
        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.unallocatedShare.from").value("2025-11-01"))
                .andExpect(jsonPath("$.summary.unallocatedShare.to").value("2025-12-31"));
    }

    @Test
    @DisplayName("OBS-013 the agreement's coefficient and leaving rows report the first and last local day of their row window")
    void rowsReportTheirWindowDates() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[0].windowFrom").value("2025-11-01"))
                .andExpect(jsonPath("$.coefficients[0].windowTo").value("2025-12-31"))
                .andExpect(jsonPath("$.leavingRows[0].windowFrom").value("2025-11-01"))
                .andExpect(jsonPath("$.leavingRows[0].windowTo").value("2025-12-31"));
        // The silent supply's row has no observed date: it spans A's whole window.
        data.coefficient(silent, plant, a, "0.1", LocalDate.parse("2025-08-25"), CHANGE);
        perform(agreementUrl(plant.getId(), a.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + silent.getId() + "')].windowFrom")
                        .value(contains("2025-09-01")))
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + silent.getId() + "')].windowTo")
                        .value(contains("2025-10-31")));
    }

    @Test
    @DisplayName("OBS-010 a coefficient row reports its stored end as an exclusive local day, null when open")
    void rowsReportTheirStoredEnd() throws Exception {
        // Stored at 00:00 UTC of the change, 01:00 in Madrid: the same local day.
        data.coefficientAt(silent, plant, a, "0.1", LocalDate.parse("2025-08-25").atStartOfDay(ObservedSharingTestData.ZONE)
                .toInstant(), CHANGE.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        String admin = loginAsCommunityAdmin(community.getId());

        perform(agreementUrl(plant.getId(), a.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + staying.getId() + "')].storedValidTo")
                        .value(contains("2025-11-01")))
                .andExpect(jsonPath("$.coefficients[?(@.supply.id == '" + silent.getId() + "')].storedValidTo")
                        .value(contains("2025-11-01")));
        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients[0].storedValidFrom").value("2025-11-01"))
                .andExpect(jsonPath("$.coefficients[0].storedValidTo").value(nullValue()));
    }

    @Test
    @DisplayName("OBS-011 the plant supply's coefficient is listed as not observable, counted, and in the share without data")
    void plantSupplyCoefficientIsNotObservable() throws Exception {
        data.coefficient(plant.getSupply(), plant, b, "0.01", CHANGE, null);
        String admin = loginAsCommunityAdmin(community.getId());
        String plantRow = "$.coefficients[?(@.supply.id == '" + plant.getSupply().getId() + "')]";

        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coefficients").value(hasSize(2)))
                .andExpect(jsonPath(plantRow + ".status").value(contains("NOT_OBSERVABLE")))
                .andExpect(jsonPath(plantRow + ".storedValidFrom").value(contains("2025-11-01")))
                .andExpect(jsonPath(plantRow + ".observedDate").value(contains(nullValue())))
                .andExpect(jsonPath(plantRow + ".proposedDate").value(contains(nullValue())))
                .andExpect(jsonPath(plantRow + ".observedBeta").value(contains(nullValue())))
                .andExpect(jsonPath(plantRow + ".observedValidDays").value(contains(0)))
                .andExpect(jsonPath("$.summary.coefficientStatusCounts.notObservable").value(1));
        perform(plantUrl(plant.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.suppliesWithValidDays[*].id")
                        .value(containsInAnyOrder(staying.getId().toString(), leaving.getId().toString())))
                .andExpect(jsonPath("$.sharingAgreements[0].coefficientStatusCounts.notObservable").value(0))
                .andExpect(jsonPath("$.sharingAgreements[1].coefficientStatusCounts.notObservable").value(1))
                .andExpect(jsonPath("$.sharingAgreements[1].coefficientStatusCounts.appliedSameDate").value(1))
                .andExpect(jsonPath("$.sharingAgreements[1].leavingStatusCounts.notObservable").value(0))
                .andExpect(jsonPath("$.sharingAgreements[1].unallocatedShare.registeredShareWithoutData")
                        .value(closeTo(0.01, 1e-9), Double.class))
                .andExpect(jsonPath("$.sharingAgreements[1].recommendations").value(contains("NONE")));
    }

    @Test
    @DisplayName("OBS-007 reading the observed sharing writes nothing")
    void readingWritesNothing() throws Exception {
        String before = snapshot();
        String admin = loginAsCommunityAdmin(community.getId());

        perform(plantUrl(plant.getId()), admin).andExpect(status().isOk());
        perform(agreementUrl(plant.getId(), b.getId()), admin).andExpect(status().isOk());

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("OBS-006 a supply holding coefficients only in another plant of the community is left out of the plant's analysis")
    void supplyOnlyInAnotherPlantIsLeftOut() throws Exception {
        // The community's second plant shares its production with this supply, which receives a sizeable share
        // while the observed plant applies B.
        Supply elsewhere = data.supply(community);
        data.coefficient(elsewhere, otherPlantOfTheCommunity, agreementOfTheOtherPlant, "1", CHANGE, null);
        data.supplyReceives(elsewhere, CHANGE, LAST_DAY, 0.2f * HOURLY_PRODUCTION_KWH);
        String admin = loginAsCommunityAdmin(community.getId());
        String id = elsewhere.getId().toString();

        perform(plantUrl(plant.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.suppliesWithValidDays[*].id").value(
                        containsInAnyOrder(staying.getId().toString(), leaving.getId().toString())))
                .andExpect(jsonPath("$.analysis.suppliesWithoutValidDays[*].id").value(
                        containsInAnyOrder(silent.getId().toString())))
                .andExpect(jsonPath("$.analysis.notComputableSupplies").value(hasSize(0)))
                .andExpect(jsonPath("$..supply[?(@.id == '" + id + "')]").value(hasSize(0)))
                // Counted, its 20 % would make the production no supply received negative.
                .andExpect(jsonPath("$.sharingAgreements[1].unallocatedShare.unallocated")
                        .value(closeTo(0.0, 1e-6)));
        perform(agreementUrl(plant.getId(), b.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..supply[?(@.id == '" + id + "')]").value(hasSize(0)));
    }

    @Test
    @DisplayName("OBS-006 the observed sharing of a draft agreement is refused with 409")
    void draftIsRefused() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        perform(agreementUrl(plant.getId(), draft.getId()), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_NOT_OBSERVABLE"));
    }

    @Test
    @DisplayName("OBS-006 a plant whose supply has no data is reported not computable")
    void plantWithoutDataIsNotComputable() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        perform(plantUrl(otherPlantOfTheCommunity.getId()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.computable").value(false))
                .andExpect(jsonPath("$.analysis.notComputableReason").value("PLANT_SUPPLY_WITHOUT_DATA"))
                .andExpect(jsonPath("$.analysis.analysedFrom").value(nullValue()))
                .andExpect(jsonPath("$.sharingAgreements[0].sharingAgreement.id")
                        .value(agreementOfTheOtherPlant.getId().toString()))
                .andExpect(jsonPath("$.sharingAgreements[0].recommendations").value(hasSize(0)));
    }

    @Test
    @DisplayName("OBS-007 both operations answer 401 without a token")
    void unauthorizedWithoutToken() throws Exception {
        for (String url : bothUrls()) {
            mockMvc.perform(get(url)).andDo(print()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("OBS-007 both operations answer 403 to a member of the plant's community who is not an admin")
    void forbiddenForMember() throws Exception {
        String member = loginAsCommunityMember(community.getId());

        for (String url : bothUrls()) {
            perform(url, member).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("OBS-007 both operations answer 404 to an admin of another community")
    void notFoundForAdminOfAnotherCommunity() throws Exception {
        String otherAdmin = loginAsCommunityAdmin(otherCommunity.getId());

        for (String url : bothUrls()) {
            perform(url, otherAdmin).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("OBS-007 both operations answer 404 to a platform admin who is not a member of the community")
    void notFoundForPlatformAdminWithoutMembership() throws Exception {
        String platformAdmin = loginAsDefaultPlatformAdmin();

        for (String url : bothUrls()) {
            perform(url, platformAdmin).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("OBS-007 the agreement operation answers 404 for an agreement of another plant")
    void notFoundForAgreementOfAnotherPlant() throws Exception {
        String admin = loginAsCommunityAdmin(community.getId());

        perform(agreementUrl(plant.getId(), agreementOfTheOtherPlant.getId()), admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_NOT_FOUND"));
        perform(agreementUrl(plant.getId(), UUID.randomUUID()), admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("SHARING_AGREEMENT_NOT_FOUND"));
    }

    /**
     * A supply observed at its value in A, then in B, that receives 4.12 % of the production on 1-5 December:
     * an unknown coefficient attributed to B.
     */
    private Supply supplyWithAnUnknownCoefficientInB() {
        Supply deviating = data.supply(community);
        data.coefficient(deviating, plant, a, "0.05", LocalDate.parse("2025-08-25"), CHANGE);
        data.coefficient(deviating, plant, b, "0.06", CHANGE, null);
        data.supplyReceives(deviating, FIRST_DAY, CHANGE.minusDays(1), 0.05f * HOURLY_PRODUCTION_KWH);
        data.supplyReceives(deviating, CHANGE, LAST_DAY, 0.06f * HOURLY_PRODUCTION_KWH);
        data.supplyReceives(deviating, LocalDate.parse("2025-12-01"), LocalDate.parse("2025-12-05"),
                0.0412f * HOURLY_PRODUCTION_KWH);
        return deviating;
    }

    private List<String> bothUrls() {
        return List.of(plantUrl(plant.getId()), agreementUrl(plant.getId(), b.getId()));
    }

    private ResultActions perform(String url, String authHeader) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, authHeader)).andDo(print());
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static int sum(JsonNode counts) {
        int sum = 0;
        for (JsonNode count : counts) {
            sum += count.asInt();
        }
        return sum;
    }

    /**
     * Every stored field of the plant's agreements and coefficients, to compare before and after a read.
     */
    private String snapshot() {
        StringBuilder snapshot = new StringBuilder();
        for (SharingAgreement agreement : getSharingAgreementRepository.findByPlantId(plant.getId(), null)) {
            snapshot.append(agreement.getId()).append(agreement.getStatus()).append(agreement.getName())
                    .append(agreement.getDistributorFileSentOn()).append(agreement.getUpdatedAt()).append('\n');
        }
        getSupplyPartitionCoefficientRepository.findAllByPlantId(plant.getId()).stream()
                .sorted(java.util.Comparator.comparing(SupplyPartitionCoefficient::getId))
                .forEach(row -> snapshot.append(row.getId()).append(row.getCoefficient()).append(row.getValidFrom())
                        .append(row.getValidTo()).append('\n'));
        return snapshot.toString();
    }

    private static String plantUrl(UUID plantId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/observation";
    }

    private static String agreementUrl(UUID plantId, UUID sharingAgreementId) {
        return "/api/v1/plants/" + plantId + "/sharing-agreements/" + sharingAgreementId + "/observation";
    }
}
