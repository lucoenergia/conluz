package org.lucoenergia.conluz.infrastructure.production.datadis.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The manual Datadis production syncs, through the real services: a supply of the community that
 * is not linked to a plant has no Datadis production, so it is answered as not found. The other
 * controller tests of these endpoints mock the service and can only check how its exceptions map.
 */
@Transactional
class SyncDatadisProductionNonPlantSupplyControllerTest extends BaseControllerTest {

    private static final String MONTHLY_URL =
            "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/datadis/sync/monthly";
    private static final String YEARLY_URL =
            "/api/v1/communities/" + DEFAULT_COMMUNITY_ID + "/production/datadis/sync/yearly";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    private Supply nonPlantSupply;
    private Supply plantSupply;

    @BeforeEach
    void setUp() {
        setDatadisConfigurationRepository.setDatadisConfiguration(DEFAULT_COMMUNITY_ID, new DatadisConfig.Builder()
                .setUsername("user").setPassword("password").setEnabled(true).build());
        nonPlantSupply = persistSupply();
        plantSupply = persistSupply();
        createPlantRepository.create(PlantMother.random().build(), SupplyId.of(plantSupply.getId()));
    }

    @Test
    @DisplayName("DCA-004 the monthly sync of one month answers 404 for a supply of the community not linked to a plant")
    void monthlySyncOfOneMonthAnswersNotFoundForANonPlantSupply() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        assertSupplyNotFound(perform(MONTHLY_URL, authHeader,
                new SyncMonthlyDatadisProductionBody(2024, 6, nonPlantSupply.getCode())));
    }

    @Test
    @DisplayName("DCA-004 the monthly sync of a whole year answers 404 for a supply of the community not linked to a plant")
    void monthlySyncOfAWholeYearAnswersNotFoundForANonPlantSupply() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        assertSupplyNotFound(perform(MONTHLY_URL, authHeader,
                new SyncMonthlyDatadisProductionBody(2024, null, nonPlantSupply.getCode())));
    }

    @Test
    @DisplayName("DCA-004 the yearly sync answers 404 for a supply of the community not linked to a plant")
    void yearlySyncAnswersNotFoundForANonPlantSupply() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        assertSupplyNotFound(perform(YEARLY_URL, authHeader,
                new SyncYearlyDatadisProductionBody(2024, nonPlantSupply.getCode())));
    }

    @Test
    @DisplayName("DCA-004 both syncs accept a supply of the community linked to a plant")
    void bothSyncsAcceptAPlantSupply() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        perform(MONTHLY_URL, authHeader, new SyncMonthlyDatadisProductionBody(2024, 6, plantSupply.getCode()))
                .andExpect(status().isOk());
        perform(YEARLY_URL, authHeader, new SyncYearlyDatadisProductionBody(2024, plantSupply.getCode()))
                .andExpect(status().isOk());
    }

    private Supply persistSupply() {
        User owner = UserMother.randomUser();
        owner.enable();
        createUserRepository.create(owner);
        return createSupplyRepository.create(SupplyMother.random(owner).withEnabled(true).build(),
                UserId.of(owner.getId()), DEFAULT_COMMUNITY_ID);
    }

    private ResultActions perform(String url, String authHeader, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** A 404 for the supply, not for the community: the message names the supply code. */
    private void assertSupplyNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message", containsString(nonPlantSupply.getCode())));
    }
}
