package org.lucoenergia.conluz.infrastructure.production.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.EnergyProductionInfluxLoader;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.SharingAgreementStatus;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetHourlyProductionControllerTest extends BaseControllerTest {

    private static final String START_DATE = "2023-09-01T00:00:00.000+02:00";
    private static final String END_DATE = "2023-09-01T23:00:00.000+02:00";

    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private EnergyProductionInfluxLoader energyProductionInfluxLoader;
    @Autowired
    private PlantRepository plantRepository;
    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private SupplyPartitionCoefficientRepository supplyPartitionCoefficientRepository;

    private Plant plant;

    @BeforeEach
    void beforeEach() {
        energyProductionInfluxLoader.loadData();
        // Register a plant in the default community whose code matches the seeded InfluxDB station_code,
        // so the community-scoped production query resolves data.
        User plantOwner = createUserRepository.create(UserMother.randomUser());
        Supply plantSupply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(plantOwner.getId()));
        plant = createPlantRepository.create(
                PlantMother.random(plantSupply).withProviderCode(EnergyProductionInfluxLoader.STATION_CODE).build(),
                SupplyId.of(plantSupply.getId()));
    }

    @AfterEach
    void afterEach() {
        energyProductionInfluxLoader.clearData();
    }

    /**
     * Registers an open-ended, coefficient-1.0 timeline row linking {@code supplyId} to {@link #plant},
     * so the per-supply production path (driven entirely by supply_partition_coefficient) resolves
     * this plant for the supply. Without this, a supply with no timeline row yields zero production
     * by design -- see CoefficientResolver.
     */
    private void persistCoefficient(UUID supplyId) {
        SharingAgreementEntity agreement = new SharingAgreementEntity();
        agreement.setId(UUID.randomUUID());
        agreement.setPlant(plantRepository.getReferenceById(plant.getId()));
        agreement.setName("Test agreement " + UUID.randomUUID());
        agreement.setStatus(SharingAgreementStatus.PUBLISHED);
        agreement.setCreatedAt(Instant.now());
        agreement.setCreatedBy(null);
        sharingAgreementRepository.save(agreement);

        supplyPartitionCoefficientRepository.save(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(supplyId)
                .withPlantId(plant.getId())
                .withSharingAgreementId(agreement.getId())
                .withCoefficient(BigDecimal.ONE)
                .withValidFrom(Instant.parse("2020-01-01T00:00:00Z"))
                .withValidTo(null)
                .withCreatedAt(Instant.now())
                .build());
    }

    @Test
    void testGetHourlyProduction() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    void testInvalidDateFormat() throws Exception {

        String invalidStartDate = "foo";
        String validEndDate = "2023-10-25T23:00:00.000+02:00";

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", invalidStartDate)
                        .queryParam("endDate", validEndDate))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString("\"message\":\"El campo con nombre 'startDate' y valor 'foo' tiene un formato incorrecto. El formato esperado es 'yyyy-mm-ddThh:mm:ss.000+h:mm'\"")));
    }

    @Test
    void testGetHourlyProductionBySupply() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());

        // Create some supplies
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()));
        createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()));
        createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()));
        persistCoefficient(supply.getId());

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("supplyId", supply.getId().toString())
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    void testGetHourlyProductionByUnknownSupply() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();
        UUID supplyId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("supplyId", supplyId.toString())
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":404")))
                .andExpect(content().string(containsString(String.format("\"message\":\"El punto de suministro con identificador '%s' no ha sido encontrado. Revise que el identificador sea correcto.\"", supplyId))));
    }

    @Test
    void testGetHourlyProductionWithWrongSupplyParameterName() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();
        String supplyId = "1";

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("supply", supplyId)
                        .queryParam("startDate", START_DATE)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("power")));
    }

    @Test
    void testGetHourlyProductionWithoutStartDate() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("endDate", END_DATE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().string(containsString("\"message\":\"El parámetro con nombre 'startDate' es obligatorio.\"")));
    }

    @Test
    void testGetHourlyProductionWithoutEndDate() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/hourly")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("startDate", START_DATE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("\"traceId\":")))
                .andExpect(content().string(containsString("\"timestamp\":")))
                .andExpect(content().string(containsString("\"status\":400")))
                .andExpect(content().encoding(StandardCharsets.UTF_8))
                .andExpect(content().string(containsString("\"message\":\"El parámetro con nombre 'endDate' es obligatorio.\"")));
    }
}
