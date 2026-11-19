package org.lucoenergia.conluz.infrastructure.production.sharingagreement.observation;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The plant operation on a plant the size of the largest one in production: 32 supplies with hourly data from
 * 2024-02-01 to the latest published month, October 2026, and four agreements, one of them a synthetic agreement
 * without a sent date. The plant supply has the shape the Datadis sync gave it (#604): its legacy surplus from
 * 2023-06-01, then production from 2024-12-01 with the surplus stored as 0, and no self-consumption. Computed on
 * read, it must answer in under 5 s (#579, D2).
 */
class PlantSharingObservationPerformanceTest extends BaseControllerTest {

    private static final int SUPPLIES = 32;
    private static final LocalDate FIRST_LEGACY_DAY = LocalDate.parse("2023-06-01");
    private static final LocalDate FIRST_PRODUCTION_DAY = LocalDate.parse("2024-12-01");
    private static final LocalDate FIRST_DAY = LocalDate.parse("2024-02-01");
    private static final LocalDate LAST_DAY = LocalDate.parse("2026-10-31");
    // The day each agreement starts to apply, and the day after the data ends.
    private static final List<LocalDate> STARTS = List.of(LocalDate.parse("2023-01-01"),
            LocalDate.parse("2024-09-01"), LocalDate.parse("2025-03-01"), LocalDate.parse("2025-11-01"));
    private static final float HOURLY_PRODUCTION_KWH = 5f;
    private static final Duration LIMIT = Duration.ofSeconds(5);

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

    @AfterEach
    void tearDown() {
        if (data != null) {
            data.clear();
        }
    }

    @Test
    @DisplayName("OBS-007 a plant of 32 supplies with data since February 2024 is observed in under 5 s")
    void largePlantIsObservedInUnderFiveSeconds() throws Exception {
        travelTo(Instant.parse("2026-11-18T10:00:00Z"));
        data = new ObservedSharingTestData(createUserRepository, createCommunityRepository, createSupplyRepository,
                createPlantRepository, createSharingAgreementRepository,
                saveSupplyPartitionCoefficientRepository, consumptionFixture, productionFixture);
        Community community = data.community();
        Plant plant = data.plant(community, 100);
        List<Supply> supplies = new ArrayList<>();
        for (int i = 0; i < SUPPLIES; i++) {
            supplies.add(data.supply(community));
        }
        List<SharingAgreement> agreements = List.of(
                data.agreement(plant, "Synthetic", SharingAgreementStatus.SUPERSEDED, null),
                data.agreement(plant, "2024 autumn", SharingAgreementStatus.SUPERSEDED, LocalDate.parse("2024-08-20")),
                data.agreement(plant, "2025 spring", SharingAgreementStatus.SUPERSEDED, LocalDate.parse("2025-02-20")),
                data.agreement(plant, "2025 autumn", SharingAgreementStatus.PUBLISHED, LocalDate.parse("2025-10-20")));

        data.plantHadLegacySurplus(plant, FIRST_LEGACY_DAY, FIRST_PRODUCTION_DAY.minusDays(1),
                HOURLY_PRODUCTION_KWH);
        data.plantProduces(plant, FIRST_PRODUCTION_DAY, LAST_DAY, HOURLY_PRODUCTION_KWH);
        for (int k = 0; k < agreements.size(); k++) {
            List<BigDecimal> coefficients = coefficients(k);
            LocalDate start = STARTS.get(k);
            LocalDate next = k + 1 < STARTS.size() ? STARTS.get(k + 1) : null;
            for (int s = 0; s < SUPPLIES; s++) {
                data.coefficient(supplies.get(s), plant, agreements.get(k), coefficients.get(s).toPlainString(),
                        start, next);
                LocalDate first = start.isBefore(FIRST_DAY) ? FIRST_DAY : start;
                LocalDate last = next == null ? LAST_DAY : next.minusDays(1);
                data.supplyReceives(supplies.get(s), first, last,
                        coefficients.get(s).floatValue() * HOURLY_PRODUCTION_KWH);
            }
        }
        String admin = loginAsCommunityAdmin(community.getId());

        long start = System.nanoTime();
        MvcResult result = mockMvc.perform(get("/api/v1/plants/" + plant.getId() + "/sharing-agreements/observation")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        System.out.printf("Observed sharing of %d supplies from %s to %s answered in %d ms%n",
                SUPPLIES, FIRST_DAY, LAST_DAY, elapsed.toMillis());

        assertThat(elapsed).isLessThan(LIMIT);
        jsonPath("$.analysis.analysedFrom").value(FIRST_LEGACY_DAY.toString()).match(result);
        jsonPath("$.analysis.analysedTo").value(LAST_DAY.toString()).match(result);
        jsonPath("$.analysis.suppliesWithValidDays.length()").value(SUPPLIES).match(result);
        for (int k = 0; k < agreements.size(); k++) {
            jsonPath("$.sharingAgreements[" + k + "].sharingAgreement.id")
                    .value(agreements.get(k).getId().toString()).match(result);
            jsonPath("$.sharingAgreements[" + k + "].coefficientStatusCounts.appliedSameDate")
                    .value(SUPPLIES).match(result);
        }
    }

    /**
     * Agreement {@code k}'s coefficients: uneven shares summing to exactly 1, different from one agreement to
     * the next for every supply.
     */
    private static List<BigDecimal> coefficients(int k) {
        List<BigDecimal> weights = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int s = 0; s < SUPPLIES; s++) {
            BigDecimal weight = BigDecimal.valueOf(10 + ((s * 7 + k * 11) % 23));
            weights.add(weight);
            total = total.add(weight);
        }
        List<BigDecimal> coefficients = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO;
        for (int s = 0; s < SUPPLIES - 1; s++) {
            BigDecimal coefficient = weights.get(s).divide(total, 6, RoundingMode.HALF_UP);
            coefficients.add(coefficient);
            assigned = assigned.add(coefficient);
        }
        coefficients.add(BigDecimal.ONE.subtract(assigned));
        return coefficients;
    }
}
