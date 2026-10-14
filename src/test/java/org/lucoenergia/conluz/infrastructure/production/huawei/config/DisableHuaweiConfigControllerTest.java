package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.web.error.RestErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DisableHuaweiConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/plants/%s/production/huawei/config/disable";
    private static final String CONFIG_URL_TEMPLATE = "/api/v1/plants/%s/production/huawei/config";

    private static final String USERNAME = "seedUser";
    private static final String PASSWORD = "seedPassword";
    private static final String BASE_URL = HuaweiConfig.DEFAULT_BASE_URL;

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    @Autowired
    private GetHuaweiConfigRepository getHuaweiConfigRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("HCF-004 AC1 disabling a complete enabled configuration changes only enabled")
    void disablingCompleteConfigurationChangesOnlyEnabled() throws Exception {
        UUID plantId = createPlant();
        seed(plantId, USERNAME, BASE_URL, PASSWORD, true);

        disable(plantId).andExpect(status().isOk()).andExpect(content().string(""));

        assertStored(plantId, USERNAME, BASE_URL, PASSWORD, false);
    }

    static Stream<Arguments> incompleteEnabledConfigurations() {
        return Stream.of(
                Arguments.of(USERNAME, BASE_URL, null),
                Arguments.of(USERNAME, BASE_URL, ""),
                Arguments.of(" ", BASE_URL, PASSWORD),
                Arguments.of(USERNAME, " ", PASSWORD)
        );
    }

    @ParameterizedTest
    @MethodSource("incompleteEnabledConfigurations")
    @DisplayName("HCF-004 AC4 disabling an incomplete enabled configuration succeeds and changes only enabled")
    void disablingIncompleteConfigurationSucceeds(String username, String baseUrl, String password) throws Exception {
        UUID plantId = createPlant();
        seed(plantId, username, baseUrl, password, true);

        disable(plantId).andExpect(status().isOk());

        assertStored(plantId, username, baseUrl, password, false);
    }

    @Test
    @DisplayName("HCF-004 AC5 disabling a plant without a configuration is not found and creates none")
    void disablingWithoutConfigurationIsNotFound() throws Exception {
        UUID plantId = createPlant();

        disable(plantId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.HUAWEI_CONFIG_NOT_FOUND.name()));

        assertThat(stored(plantId)).isEmpty();
    }

    @Test
    @DisplayName("AC6 a plant id that is not a UUID is rejected")
    void malformedPlantIdIsRejected() throws Exception {
        perform(post(String.format(URL_TEMPLATE, "not-a-uuid"))
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    @DisplayName("AC7 without a token the request is unauthorized, like the configuration write, and changes nothing")
    void withoutTokenIsUnauthorized() throws Exception {
        assertSameStatusAsConfigurationWrite(null, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AC7 a caller who is not a member of the plant's community gets not-found, like the configuration write, and changes nothing")
    void nonMemberGetsNotFound() throws Exception {
        assertSameStatusAsConfigurationWrite(loginAsPartner(), HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("AC7 a member who is not a community admin is forbidden, like the configuration write, and changes nothing")
    void memberWithoutAdminRoleIsForbidden() throws Exception {
        assertSameStatusAsConfigurationWrite(loginAsCommunityMember(DEFAULT_COMMUNITY_ID), HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AC7 a platform admin who is not a member gets not-found, like the configuration write, and changes nothing")
    void platformAdminWithoutMembershipGetsNotFound() throws Exception {
        assertSameStatusAsConfigurationWrite(loginAsDefaultPlatformAdmin(), HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("HCF-004 disabling an already disabled configuration succeeds")
    void disablingDisabledConfigurationSucceeds() throws Exception {
        UUID plantId = createPlant();
        seed(plantId, USERNAME, BASE_URL, PASSWORD, false);

        disable(plantId).andExpect(status().isOk());

        assertStored(plantId, USERNAME, BASE_URL, PASSWORD, false);
    }

    @Test
    @DisplayName("HCF-004 disabling one plant's configuration leaves another plant's untouched")
    void otherPlantIsUntouched() throws Exception {
        UUID plantId = createPlant();
        UUID otherPlantId = createPlant();
        seed(plantId, USERNAME, BASE_URL, PASSWORD, true);
        seed(otherPlantId, "otherUser", BASE_URL, "otherPassword", true);

        disable(plantId).andExpect(status().isOk());

        assertStored(plantId, USERNAME, BASE_URL, PASSWORD, false);
        assertStored(otherPlantId, "otherUser", BASE_URL, "otherPassword", true);
    }

    /**
     * The configuration write runs first and is rejected, so the two answers are compared over the same,
     * unchanged configuration.
     */
    private void assertSameStatusAsConfigurationWrite(String authHeader, HttpStatus expected) throws Exception {
        UUID plantId = createPlant();
        seed(plantId, USERNAME, BASE_URL, PASSWORD, true);

        MockHttpServletRequestBuilder configurationWrite = put(String.format(CONFIG_URL_TEMPLATE, plantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "newUser", "password": "newPassword", "baseUrl": "%s", "enabled": false}
                        """.formatted(BASE_URL));
        MockHttpServletRequestBuilder disableRequest = post(String.format(URL_TEMPLATE, plantId));
        if (authHeader != null) {
            configurationWrite.header(HttpHeaders.AUTHORIZATION, authHeader);
            disableRequest.header(HttpHeaders.AUTHORIZATION, authHeader);
        }

        perform(configurationWrite).andExpect(status().is(expected.value()));
        perform(disableRequest).andExpect(status().is(expected.value()));

        assertStored(plantId, USERNAME, BASE_URL, PASSWORD, true);
    }

    private UUID createPlant() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()));
        return createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId())).getId();
    }

    /**
     * Through the repository's upsert rather than the configuration write, which validates its body: rows stored
     * before that validation existed may hold any of these values.
     */
    private void seed(UUID plantId, String username, String baseUrl, String password, boolean enabled) {
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, new HuaweiConfig.Builder()
                .setUsername(username)
                .setPassword(password)
                .setBaseUrl(baseUrl)
                .setEnabled(enabled)
                .build());
    }

    private ResultActions disable(UUID plantId) throws Exception {
        return perform(post(String.format(URL_TEMPLATE, plantId))
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID)));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andDo(print());
    }

    /**
     * Reads the row back from the database rather than from the persistence context, so an assertion sees
     * what the request wrote, not what a managed entity holds in memory.
     */
    private Optional<HuaweiConfig> stored(UUID plantId) {
        entityManager.flush();
        entityManager.clear();
        return getHuaweiConfigRepository.getHuaweiConfig(plantId);
    }

    private void assertStored(UUID plantId, String username, String baseUrl, String password, boolean enabled) {
        HuaweiConfig stored = stored(plantId).orElseThrow();
        assertThat(stored.getUsername()).isEqualTo(username);
        assertThat(stored.getBaseUrl()).isEqualTo(baseUrl);
        assertThat(stored.getPassword()).isEqualTo(password);
        assertThat(stored.getEnabled()).isEqualTo(enabled);
    }
}
