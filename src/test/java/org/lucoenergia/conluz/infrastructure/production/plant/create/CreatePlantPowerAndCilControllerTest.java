package org.lucoenergia.conluz.infrastructure.production.plant.create;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The peak and nominal power and the CIL of a plant on {@code createPlant} (#567), read back through
 * {@code getPlantById} and {@code getAllPlants}.
 */
@Transactional
class CreatePlantPowerAndCilControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/plants";
    private static final String CIL = "ES0031406912345678AB1F001";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private PlantRepository plantRepository;

    // --- PLT-003: nominal power ---

    @Test
    @DisplayName("PLT-003 AC1 the peak and nominal power are stored and read back by getPlantById and getAllPlants")
    void thePeakAndNominalPowerAreStoredAndReadBack() throws Exception {
        String admin = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        Map<String, Object> first = body("PS-POWER-1", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        first.put("peakPowerKWp", 80.0);
        first.put("nominalPowerKW", 60.0);
        Map<String, Object> second = body("PS-POWER-2", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        second.put("peakPowerKWp", 20.0);
        second.put("nominalPowerKW", 15.0);

        String firstId = createdId(create(admin, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakPowerKWp").value(80.0))
                .andExpect(jsonPath("$.nominalPowerKW").value(60.0)));
        String secondId = createdId(create(admin, second).andExpect(status().isOk()));

        mockMvc.perform(get(URL + "/" + firstId).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakPowerKWp").value(80.0))
                .andExpect(jsonPath("$.nominalPowerKW").value(60.0));
        mockMvc.perform(get("/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/plants")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + firstId + "')].peakPowerKWp").value(contains(80.0)))
                .andExpect(jsonPath("$.items[?(@.id == '" + firstId + "')].nominalPowerKW").value(contains(60.0)))
                .andExpect(jsonPath("$.items[?(@.id == '" + secondId + "')].peakPowerKWp").value(contains(20.0)))
                .andExpect(jsonPath("$.items[?(@.id == '" + secondId + "')].nominalPowerKW").value(contains(15.0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "0", "-1"})
    @DisplayName("PLT-003 AC2 a nominal power that is missing or not above 0 is refused with 400, and nothing is created")
    void aNominalPowerNotAboveZeroIsRefused(String nominalPower) throws Exception {
        Map<String, Object> body = body("PS-NOMINAL-BAD", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        if (nominalPower.equals("missing")) {
            body.remove("nominalPowerKW");
        } else {
            body.put("nominalPowerKW", Double.parseDouble(nominalPower));
        }

        create(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), body).andExpect(status().isBadRequest());

        assertEquals(0, plantRepository.countByProviderCode("PS-NOMINAL-BAD"));
    }

    @Test
    @DisplayName("PLT-003 AC3 a nominal power above the peak power is accepted")
    void aNominalPowerAboveThePeakPowerIsAccepted() throws Exception {
        Map<String, Object> body = body("PS-OVERSIZED", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        body.put("peakPowerKWp", 80.0);
        body.put("nominalPowerKW", 90.0);

        create(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakPowerKWp").value(80.0))
                .andExpect(jsonPath("$.nominalPowerKW").value(90.0));
    }

    // --- PLT-004: CIL format ---

    @ParameterizedTest
    @ValueSource(strings = {" es0031406912345678ab1f001 ", "ES00 3140 6912 3456 78AB 1F00 1",
            " ES0031406912 345678AB1F001 ", "\tES0031406912345678AB1F001\n"})
    @DisplayName("PLT-004 AC4 the CIL is upper-cased and stripped of all whitespace, no-break spaces included")
    void theCilIsNormalised(String cil) throws Exception {
        Map<String, Object> body = body("PS-CIL", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        body.put("cil", cil);
        String admin = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String id = createdId(create(admin, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cil").value(CIL)));

        mockMvc.perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cil").value(CIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ES0031406912345678AB1F00", "ES0031406912345678AB1F0011", "ES0031406912345678AB1F-01",
            "ES0031406912345678AB1FÑ01", "ES0031406912345678AB1Fß01"})
    @DisplayName("PLT-004 AC5 a CIL that is not 25 ASCII alphanumeric characters once normalised is refused with "
            + "400 PLANT_CIL_INVALID, and nothing is created")
    void aCilThatIsNotOneIsRefused(String cil) throws Exception {
        Map<String, Object> body = body("PS-CIL-BAD", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        body.put("cil", cil);

        create(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PLANT_CIL_INVALID"));

        assertEquals(0, plantRepository.countByProviderCode("PS-CIL-BAD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "", "   ", " \t"})
    @DisplayName("PLT-004 AC6 a missing, empty or blank CIL means no CIL")
    void aMissingEmptyOrBlankCilMeansNoCil(String cil) throws Exception {
        Map<String, Object> body = body("PS-NO-CIL", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        if (!cil.equals("missing")) {
            body.put("cil", cil);
        }
        String admin = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String id = createdId(create(admin, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cil").value(nullValue())));

        mockMvc.perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.cil").value(nullValue()));
    }

    // --- PLT-005: CIL uniqueness ---

    @Test
    @DisplayName("PLT-005 AC7 a CIL another plant of the same community holds, in any letter case, is refused with "
            + "409 PLANT_CIL_ALREADY_IN_USE, and nothing is created")
    void aCilAnotherPlantOfTheSameCommunityHoldsIsRefused() throws Exception {
        givenAPlantWithTheCil(DEFAULT_COMMUNITY_ID);
        Map<String, Object> body = body("PS-CIL-TAKEN", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        body.put("cil", CIL.toLowerCase());

        create(loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLANT_CIL_ALREADY_IN_USE"));

        assertEquals(0, plantRepository.countByProviderCode("PS-CIL-TAKEN"));
    }

    @Test
    @DisplayName("PLT-005 AC7 a CIL a plant of another community holds is refused with 409 PLANT_CIL_ALREADY_IN_USE, "
            + "and nothing is created")
    void aCilAPlantOfAnotherCommunityHoldsIsRefused() throws Exception {
        givenAPlantWithTheCil(DEFAULT_COMMUNITY_ID);
        UUID otherCommunity = createCommunityRepository.create(CommunityMother.random().build()).getId();
        Map<String, Object> body = body("PS-CIL-ELSEWHERE", createSupply(otherCommunity).getCode());
        body.put("cil", CIL);

        create(loginAsCommunityAdmin(otherCommunity), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("PLANT_CIL_ALREADY_IN_USE"));

        assertEquals(0, plantRepository.countByProviderCode("PS-CIL-ELSEWHERE"));
    }

    // --- Access ---

    @Test
    @DisplayName("AC15 creating a plant with peak and nominal power and a CIL is refused as before: 401 anonymous, "
            + "403 member on their own supply, 404 member on another's supply and admin of another community")
    void accessIsUnchanged() throws Exception {
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply ownSupply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(member.getId()),
                DEFAULT_COMMUNITY_ID);
        Map<String, Object> onOwnSupply = body("PS-ACCESS", ownSupply.getCode());
        onOwnSupply.put("cil", CIL);
        Map<String, Object> onAnotherSupply = body("PS-ACCESS", createSupply(DEFAULT_COMMUNITY_ID).getCode());
        onAnotherSupply.put("cil", CIL);
        UUID otherCommunity = createCommunityRepository.create(CommunityMother.random().build()).getId();
        String memberToken = loginUser(member);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onAnotherSupply)))
                .andExpect(status().isUnauthorized());
        create(memberToken, onOwnSupply).andExpect(status().isForbidden());
        create(memberToken, onAnotherSupply).andExpect(status().isNotFound());
        create(loginAsCommunityAdmin(otherCommunity), onAnotherSupply).andExpect(status().isNotFound());

        assertEquals(0, plantRepository.countByProviderCode("PS-ACCESS"));
    }

    private void givenAPlantWithTheCil(UUID communityId) {
        Supply supply = createSupply(communityId);
        createPlantRepository.create(PlantMother.random(supply).withCil(CIL).build(), SupplyId.of(supply.getId()));
    }

    private Supply createSupply(UUID communityId) {
        User owner = createUserRepository.create(UserMother.randomUser());
        return createSupplyRepository.create(SupplyMother.random().build(), UserId.of(owner.getId()), communityId);
    }

    private static Map<String, Object> body(String providerCode, String supplyCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("providerCode", providerCode);
        body.put("name", "Plant " + providerCode);
        body.put("supplyCode", supplyCode);
        body.put("address", "Fake Street 123");
        body.put("inverterProvider", "HUAWEI");
        body.put("peakPowerKWp", 80.0);
        body.put("nominalPowerKW", 60.0);
        return body;
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(URL)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static String createdId(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
