package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The peak and nominal power and the CIL of a plant on {@code updatePlant} (#567).
 */
@Transactional
class UpdatePlantPowerAndCilControllerTest extends BaseControllerTest {

    private static final String CIL = "ES0031406912345678AB1F001";
    private static final String OTHER_CIL = "ES0031406998765432CD2G002";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private GetPlantRepository getPlantRepository;

    // --- PLT-003: nominal power ---

    @Test
    @DisplayName("PLT-003 AC1 the updated peak and nominal power are stored and read back by getPlantById")
    void theUpdatedPeakAndNominalPowerAreStored() throws Exception {
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, null);
        Map<String, Object> body = body(plant);
        body.put("peakPowerKWp", 80.0);
        body.put("nominalPowerKW", 60.0);
        String admin = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        update(admin, plant, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakPowerKWp").value(80.0))
                .andExpect(jsonPath("$.nominalPowerKW").value(60.0));

        mockMvc.perform(get("/api/v1/plants/" + plant.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.peakPowerKWp").value(80.0))
                .andExpect(jsonPath("$.nominalPowerKW").value(60.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "0", "-1"})
    @DisplayName("PLT-003 AC2 a nominal power that is missing or not above 0 is refused with 400, and nothing is updated")
    void aNominalPowerNotAboveZeroIsRefused(String nominalPower) throws Exception {
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, CIL);
        Map<String, Object> body = body(plant);
        body.put("name", "Renamed");
        if (nominalPower.equals("missing")) {
            body.remove("nominalPowerKW");
        } else {
            body.put("nominalPowerKW", Double.parseDouble(nominalPower));
        }

        update(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), plant, body).andExpect(status().isBadRequest());

        assertUnchanged(plant);
    }

    @Test
    @DisplayName("PLT-003 a nominal power above the peak power is accepted on update")
    void aNominalPowerAboveThePeakPowerIsAccepted() throws Exception {
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, null);
        Map<String, Object> body = body(plant);
        body.put("peakPowerKWp", 80.0);
        body.put("nominalPowerKW", 90.0);

        update(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), plant, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nominalPowerKW").value(90.0));
    }

    // --- PLT-004: CIL format ---

    @Test
    @DisplayName("PLT-004 the CIL is upper-cased and stripped of all whitespace on update, no-break spaces included")
    void theCilIsNormalisedOnUpdate() throws Exception {
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, null);
        Map<String, Object> body = body(plant);
        body.put("cil", " es00 3140 6912 345678ab1f001 ");

        update(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), plant, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cil").value(CIL));

        assertEquals(CIL, reload(plant).getCil());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ES0031406912345678AB1F00", "ES0031406912345678AB1F0011", "ES0031406912345678AB1F-01"})
    @DisplayName("PLT-004 AC5 a CIL that is not 25 ASCII alphanumeric characters once normalised is refused with "
            + "400 PLANT_CIL_INVALID, and nothing is updated")
    void aCilThatIsNotOneIsRefused(String cil) throws Exception {
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, CIL);
        Map<String, Object> body = body(plant);
        body.put("name", "Renamed");
        body.put("cil", cil);

        update(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), plant, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PLANT_CIL_INVALID"));

        assertUnchanged(plant);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "", "   "})
    @DisplayName("PLT-004 AC6 updating a plant that has a CIL with a missing, empty or blank CIL clears it")
    void aMissingEmptyOrBlankCilClearsIt(String cil) throws Exception {
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, CIL);
        Map<String, Object> body = body(plant);
        if (cil.equals("missing")) {
            body.remove("cil");
        } else {
            body.put("cil", cil);
        }

        update(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), plant, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cil").value(nullValue()));

        assertNull(reload(plant).getCil());
    }

    // --- PLT-005: CIL uniqueness ---

    @Test
    @DisplayName("PLT-005 AC7 updating a plant with the CIL another plant of the same community holds, in any letter "
            + "case, is refused with 409 PLANT_CIL_ALREADY_IN_USE, and nothing is updated")
    void theCilOfAnotherPlantOfTheSameCommunityIsRefused() throws Exception {
        createPlant(DEFAULT_COMMUNITY_ID, CIL);
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, OTHER_CIL);
        Map<String, Object> body = body(plant);
        body.put("name", "Renamed");
        body.put("cil", CIL.toLowerCase());

        update(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), plant, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLANT_CIL_ALREADY_IN_USE"));

        assertUnchanged(plant);
    }

    @Test
    @DisplayName("PLT-005 AC7 updating a plant with the CIL a plant of another community holds is refused with 409 "
            + "PLANT_CIL_ALREADY_IN_USE, and nothing is updated")
    void theCilOfAPlantOfAnotherCommunityIsRefused() throws Exception {
        createPlant(DEFAULT_COMMUNITY_ID, CIL);
        UUID otherCommunity = createCommunityRepository.create(CommunityMother.random().build()).getId();
        Plant plant = createPlant(otherCommunity, null);
        Map<String, Object> body = body(plant);
        body.put("name", "Renamed");
        body.put("cil", CIL);

        update(loginAsCommunityAdmin(otherCommunity), plant, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLANT_CIL_ALREADY_IN_USE"));

        assertUnchanged(plant);
    }

    @Test
    @DisplayName("PLT-005 AC8 updating a plant that keeps its own CIL, in any letter case, is not a conflict")
    void keepingTheOwnCilIsNotAConflict() throws Exception {
        createPlant(DEFAULT_COMMUNITY_ID, OTHER_CIL);
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, CIL);
        Map<String, Object> body = body(plant);
        body.put("name", "Renamed");
        body.put("cil", " " + CIL.toLowerCase());

        update(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), plant, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.cil").value(CIL));
    }

    // --- Access ---

    @Test
    @DisplayName("AC15 updating a plant with peak and nominal power and a CIL is refused as before: 401 anonymous, "
            + "403 member, 404 admin of another community")
    void accessIsUnchanged() throws Exception {
        Plant plant = createPlant(DEFAULT_COMMUNITY_ID, null);
        Map<String, Object> body = body(plant);
        body.put("name", "Renamed");
        body.put("cil", CIL);
        UUID otherCommunity = createCommunityRepository.create(CommunityMother.random().build()).getId();

        mockMvc.perform(put("/api/v1/plants/" + plant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        update(loginAsCommunityMember(DEFAULT_COMMUNITY_ID), plant, body).andExpect(status().isForbidden());
        update(loginAsCommunityAdmin(otherCommunity), plant, body).andExpect(status().isNotFound());

        assertUnchanged(plant);
    }

    private Plant createPlant(UUID communityId, String cil) {
        User owner = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()),
                communityId);
        return createPlantRepository.create(PlantMother.random(supply).withPeakPowerKWp(50.0)
                .withNominalPowerKW(40.0).withCil(cil).build(), SupplyId.of(supply.getId()));
    }

    private static Map<String, Object> body(Plant plant) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("providerCode", plant.getProviderCode());
        body.put("regulatoryCode", plant.getRegulatoryCode());
        body.put("cil", plant.getCil());
        body.put("name", plant.getName());
        body.put("supplyCode", plant.getSupply().getCode());
        body.put("address", plant.getAddress());
        body.put("description", plant.getDescription());
        body.put("inverterProvider", plant.getInverterProvider().name());
        body.put("peakPowerKWp", plant.getPeakPowerKWp());
        body.put("nominalPowerKW", plant.getNominalPowerKW());
        return body;
    }

    private ResultActions update(String token, Plant plant, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/plants/" + plant.getId())
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Plant reload(Plant plant) {
        return getPlantRepository.findById(PlantId.of(plant.getId())).orElseThrow();
    }

    private void assertUnchanged(Plant plant) {
        Plant reloaded = reload(plant);
        assertEquals(plant.getName(), reloaded.getName());
        assertEquals(plant.getCil(), reloaded.getCil());
        assertEquals(plant.getPeakPowerKWp(), reloaded.getPeakPowerKWp());
        assertEquals(plant.getNominalPowerKW(), reloaded.getNominalPowerKW());
    }
}
