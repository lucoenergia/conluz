package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SaveSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.energy.CommunityEnergyTestFixture;
import org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.SurplusClaimTestData.PlantWithSupplies;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.infrastructure.shared.web.apidocs.ResponseContractAssertions.assertMatchesContract;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over a real PostgreSQL and InfluxDB, without a transaction around the request, so a lazy load outside
 * the service's transaction would fail here as in production. The clock is at 2026-11-10 in Madrid.
 *
 * <p>The plant has three supplies in scope: the first with records, the second with records missing an hour, the
 * third without any record, and a fourth supply whose coefficient ended before the month.
 */
class GetPlantSurplusClaimControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/plants/{plantId}/surplus-claim";
    private static final Instant NOW = Instant.parse("2026-11-10T09:30:00Z");
    private static final double TOLERANCE = 1e-9;

    @Autowired
    private CommunityEnergyTestFixture fixture;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private SaveSupplyPartitionCoefficientRepository saveSupplyPartitionCoefficientRepository;

    private SurplusClaimTestData data;

    @BeforeEach
    void setUp() {
        travelTo(NOW);
        data = new SurplusClaimTestData(fixture, createSharingAgreementRepository,
                saveSupplyPartitionCoefficientRepository);
    }

    @AfterEach
    void tearDown() {
        data.clear();
    }

    @Test
    @DisplayName("SCL-012 SCL-013 a community admin reads the month, every field present as the contract describes it")
    void aCommunityAdminReadsTheMonthAsTheContractDescribesIt() throws Exception {
        PlantWithSupplies plant = claimedPlant();

        String body = claim(plant.plant().getId(), loginAsCommunityAdmin(plant.communityId()), "2026-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.plant.id").value(plant.plant().getId().toString()))
                .andExpect(jsonPath("$.plant.name").value("North"))
                .andExpect(jsonPath("$.availableMonths.firstMonth").value("2025-01"))
                .andExpect(jsonPath("$.days", hasSize(30)))
                .andExpect(jsonPath("$.days[9].date").value("2026-09-10"))
                .andExpect(jsonPath("$.days[9].hours", hasSize(24)))
                .andExpect(jsonPath("$.days[9].hours[12].startsAt").value("2026-09-10T12:00:00+02:00"))
                .andExpect(jsonPath("$.days[9].hours[12].surplusKWh").value(closeTo(5.123, TOLERANCE)))
                .andExpect(jsonPath("$.days[9].hours[11].surplusKWh").value(nullValue()))
                .andExpect(jsonPath("$.days[9].peakHour.startsAt").value("2026-09-10T12:00:00+02:00"))
                .andExpect(jsonPath("$.days[9].peakHour.surplusKWh").value(closeTo(5.123, TOLERANCE)))
                .andExpect(jsonPath("$.days[9].state").value("ESTIMATED"))
                .andExpect(jsonPath("$.summary.surplusKWh").value(closeTo(5.123, TOLERANCE)))
                .andExpect(jsonPath("$.summary.suppliesInScope").value(3))
                .andExpect(jsonPath("$.summary.suppliesWithData").value(2))
                .andExpect(jsonPath("$.summary.realDays").value(0))
                .andExpect(jsonPath("$.summary.estimatedDays").value(30))
                .andExpect(jsonPath("$.summary.surplusIncome.amountEur").value(closeTo(0.0, TOLERANCE)))
                .andExpect(jsonPath("$.summary.surplusIncome.unpricedSurplusKWh").value(closeTo(5.123, TOLERANCE)))
                .andExpect(jsonPath("$.warnings.noAppliedCoefficients").value(false))
                .andExpect(jsonPath("$.warnings.uncoveredShare").value(nullValue()))
                .andExpect(jsonPath("$.warnings.plantSupplyShare").value(nullValue()))
                .andExpect(jsonPath("$.warnings.hoursAboveNominalPower", hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        assertMatchesContract(mockMvc, objectMapper, "PlantSurplusClaimResponse", body);
    }

    @Test
    @DisplayName("SCL-013 the warnings name the supplies concerned with their CUPS")
    void theWarningsNameTheSuppliesWithTheirCups() throws Exception {
        PlantWithSupplies plant = claimedPlant();
        Supply withGaps = plant.supplies().get(1);
        Supply withoutData = plant.supplies().get(2);

        claim(plant.plant().getId(), loginAsCommunityAdmin(plant.communityId()), "2026-09")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.suppliesWithoutData", hasSize(1)))
                .andExpect(jsonPath("$.warnings.suppliesWithoutData[0].supply.id").value(withoutData.getId().toString()))
                .andExpect(jsonPath("$.warnings.suppliesWithoutData[0].supply.code").value(withoutData.getCode()))
                .andExpect(jsonPath("$.warnings.suppliesWithoutData[0].coefficient").value(0.25))
                .andExpect(jsonPath("$.warnings.suppliesWithGaps", hasSize(2)))
                .andExpect(jsonPath("$.warnings.suppliesWithGaps[?(@.supply.code == '" + withGaps.getCode()
                        + "')].days[0]").value("2026-09-01"));
    }

    @Test
    @DisplayName("SCL-013 AC14 a community admin reads it, a member gets 403, anyone else 404, an unknown plant 404, and no token 401")
    void onlyACommunityAdminReadsIt() throws Exception {
        PlantWithSupplies plant = claimedPlant();
        UUID plantId = plant.plant().getId();
        User member = fixture.createMember(plant.communityId());

        claim(plantId, loginAsCommunityAdmin(plant.communityId()), "2026-09").andExpect(status().isOk());
        claim(plantId, loginUser(member), "2026-09").andExpect(status().isForbidden());
        claim(plantId, loginAsPartner(), "2026-09").andExpect(status().isNotFound());
        claim(plantId, loginAsDefaultPlatformAdmin(), "2026-09").andExpect(status().isNotFound());
        claim(UUID.randomUUID(), loginAsCommunityAdmin(plant.communityId()), "2026-09")
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH, plantId).queryParam("month", "2026-09")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("SCL-007 AC13 the current month is refused with 400 SURPLUS_CLAIM_MONTH_NOT_CLOSED")
    void theCurrentMonthIsRefused() throws Exception {
        PlantWithSupplies plant = claimedPlant();
        String admin = loginAsCommunityAdmin(plant.communityId());

        for (String month : new String[]{"2026-11", "2027-02"}) {
            claim(plant.plant().getId(), admin, month)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("SURPLUS_CLAIM_MONTH_NOT_CLOSED"));
        }
    }

    @Test
    @DisplayName("SCL-007 a month that is not YYYY-MM is a bad request")
    void aMalformedMonthIsABadRequest() throws Exception {
        PlantWithSupplies plant = claimedPlant();
        String admin = loginAsCommunityAdmin(plant.communityId());

        for (String month : new String[]{"2026-13", "2026/09", "september"}) {
            claim(plant.plant().getId(), admin, month).andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("SCL-007 without a month the last closed month is claimed when none is published")
    void withoutAMonthTheLastClosedMonthIsClaimed() throws Exception {
        PlantWithSupplies plant = claimedPlant();

        request(get(PATH, plant.plant().getId())
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(plant.communityId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.availableMonths.latestPublishedMonth").value(nullValue()))
                .andExpect(jsonPath("$.days", hasSize(31)))
                .andExpect(jsonPath("$.days[24].hours", hasSize(25)));
    }

    @Test
    @DisplayName("SCL-011 SCL-012 a month without an applied coefficient matches the contract, nulls and empty lists included")
    void aMonthWithoutAnAppliedCoefficientMatchesTheContract() throws Exception {
        PlantWithSupplies plant = claimedPlant();

        String body = claim(plant.plant().getId(), loginAsCommunityAdmin(plant.communityId()), "2024-03")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.noAppliedCoefficients").value(true))
                .andExpect(jsonPath("$.warnings.uncoveredShare").value(nullValue()))
                .andExpect(jsonPath("$.warnings.suppliesWithoutData", hasSize(0)))
                .andExpect(jsonPath("$.summary.surplusKWh").value(nullValue()))
                .andExpect(jsonPath("$.summary.surplusIncome").value(nullValue()))
                .andExpect(jsonPath("$.days[0].peakHour").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        assertMatchesContract(mockMvc, objectMapper, "PlantSurplusClaimResponse", body);
    }

    /**
     * Coefficients of 0.5, 0.25 and 0.25 since 2025-01-01, and one that ended on 2026-06-01. The first supply stores
     * 5.123 kWh at noon on 10 September; the second a zero at 10:00 on 1 September only; the third nothing.
     */
    private PlantWithSupplies claimedPlant() {
        PlantWithSupplies plant = data.plantWithSupplies(4);
        SharingAgreement agreement = data.agreement(plant.plant());
        data.coefficient(plant.supplies().get(0), plant.plant(), agreement, "0.5", LocalDate.of(2025, 1, 1), null);
        data.coefficient(plant.supplies().get(1), plant.plant(), agreement, "0.25", LocalDate.of(2025, 1, 1), null);
        data.coefficient(plant.supplies().get(2), plant.plant(), agreement, "0.25", LocalDate.of(2025, 1, 1), null);
        data.coefficient(plant.supplies().get(3), plant.plant(), agreement, "0.25", LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 6, 1));
        data.surplus(plant.supplies().get(0), LocalDateTime.of(2026, 9, 10, 12, 0), 5.123f);
        data.surplus(plant.supplies().get(1), LocalDateTime.of(2026, 9, 1, 10, 0), 0f);
        return plant;
    }

    private ResultActions claim(UUID plantId, String token, String month) throws Exception {
        return request(get(PATH, plantId).header(HttpHeaders.AUTHORIZATION, token).queryParam("month", month));
    }

    private ResultActions request(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }
}
