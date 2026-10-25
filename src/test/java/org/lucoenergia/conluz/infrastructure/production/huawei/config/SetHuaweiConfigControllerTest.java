package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SetHuaweiConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/plants/%s/production/huawei/config";
    private static final String ENABLE_URL_TEMPLATE = "/api/v1/plants/%s/production/huawei/config/enable";

    private static final String STORED_PASSWORD = "storedPassword";
    private static final String OTHER_BASE_URL = "https://fusionsolar.example.com/thirdData";

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
    void testNewConfigureHuawei() throws Exception {
        Plant plant = createPlant();

        ConfigureHuaweiBody body = new ConfigureHuaweiBody("testUser", "testPass",
                HuaweiConfig.DEFAULT_BASE_URL);
        String bodyAsString = objectMapper.writeValueAsString(body);

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(
                        put(String.format(URL_TEMPLATE, plant.getId()))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testUser"))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.baseUrl").value(HuaweiConfig.DEFAULT_BASE_URL))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("HCF-001 AC1 omitting the password updates the other fields and keeps the stored password")
    void omittedPasswordKeepsStoredOne() throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD);

        putConfig(plant, """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordSet").value(true));

        HuaweiConfig stored = stored(plant.getId()).orElseThrow();
        assertThat(stored.getPassword()).isEqualTo(STORED_PASSWORD);
        assertThat(stored.getUsername()).isEqualTo("newUser");
        assertThat(stored.getBaseUrl()).isEqualTo(OTHER_BASE_URL);
        assertThat(stored.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("HCF-001 omitting the password on one plant leaves another plant's configuration untouched")
    void omittedPasswordDoesNotTouchOtherPlant() throws Exception {
        Plant plant = createPlant();
        Plant otherPlant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD);
        seedConfig(otherPlant.getId(), "otherPassword");

        putConfig(plant, """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk());

        HuaweiConfig other = stored(otherPlant.getId()).orElseThrow();
        assertThat(other.getPassword()).isEqualTo("otherPassword");
        assertThat(other.getUsername()).isEqualTo("seedUser");
        assertThat(other.getBaseUrl()).isEqualTo(HuaweiConfig.DEFAULT_BASE_URL);
        assertThat(other.getEnabled()).isTrue();
        assertThat(stored(plant.getId()).orElseThrow().getPassword()).isEqualTo(STORED_PASSWORD);
    }

    @Test
    @DisplayName("AC2 a non-empty password replaces the stored one")
    void newPasswordReplacesStoredOne() throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD);

        putConfig(plant, """
                {"username": "seedUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(HuaweiConfig.DEFAULT_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordSet").value(true));

        assertThat(stored(plant.getId()).orElseThrow().getPassword()).isEqualTo("newPassword");
    }

    @Test
    @DisplayName("HCF-002 AC3 omitting the password on a plant without configuration is rejected and stores nothing")
    void omittedPasswordWithoutConfigIsRejected() throws Exception {
        Plant plant = createPlant();

        putConfig(plant, """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(HuaweiConfig.DEFAULT_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.HUAWEI_CONFIG_PASSWORD_REQUIRED.name()));

        assertThat(stored(plant.getId())).isEmpty();
    }

    @Test
    @DisplayName("HCF-002 omitting the password when the stored password is null is rejected and changes nothing")
    void omittedPasswordWithStoredNullPasswordIsRejected() throws Exception {
        assertOmittedPasswordIsRejectedOver(null);
    }

    @Test
    @DisplayName("HCF-002 omitting the password when the stored password is empty is rejected and changes nothing")
    void omittedPasswordWithStoredEmptyPasswordIsRejected() throws Exception {
        assertOmittedPasswordIsRejectedOver("");
    }

    @Test
    @DisplayName("HCF-003 AC4 an explicit null password is rejected on a plant with a configuration")
    void nullPasswordWithConfigIsRejected() throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD);

        putConfig(plant, """
                {"username": "newUser", "password": null, "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()))
                .andExpect(jsonPath("$.message").value(containsString("password")));

        assertSeedConfigUnchanged(plant.getId(), STORED_PASSWORD);
    }

    @Test
    @DisplayName("HCF-003 AC4 an explicit null password is rejected on a plant without configuration")
    void nullPasswordWithoutConfigIsRejected() throws Exception {
        Plant plant = createPlant();

        putConfig(plant, """
                {"username": "newUser", "password": null, "baseUrl": "%s"}
                """.formatted(HuaweiConfig.DEFAULT_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()));

        assertThat(stored(plant.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC5 an empty password is rejected on a plant with a configuration")
    void emptyPasswordWithConfigIsRejected() throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD);

        putConfig(plant, """
                {"username": "newUser", "password": "", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("password")));

        assertSeedConfigUnchanged(plant.getId(), STORED_PASSWORD);
    }

    @Test
    @DisplayName("AC5 an empty password is rejected on a plant without configuration")
    void emptyPasswordWithoutConfigIsRejected() throws Exception {
        Plant plant = createPlant();

        putConfig(plant, """
                {"username": "newUser", "password": "", "baseUrl": "%s"}
                """.formatted(HuaweiConfig.DEFAULT_BASE_URL))
                .andExpect(status().isBadRequest());

        assertThat(stored(plant.getId())).isEmpty();
    }

    @Test
    @DisplayName("a blank username is rejected and stores nothing")
    void blankUsernameIsRejected() throws Exception {
        Plant plant = createPlant();

        putConfig(plant, """
                {"username": " ", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(HuaweiConfig.DEFAULT_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("username")));

        assertThat(stored(plant.getId())).isEmpty();
    }

    @Test
    @DisplayName("AC6 a caller who cannot manage the plant is refused and changes nothing")
    void callerWithoutManageIsForbidden() throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD);
        String authHeader = loginAsCommunityMember(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(put(String.format(URL_TEMPLATE, plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                                """.formatted(OTHER_BASE_URL)))
                .andDo(print())
                .andExpect(status().isForbidden());

        assertSeedConfigUnchanged(plant.getId(), STORED_PASSWORD);
    }

    @Test
    @DisplayName("HCF-006 AC1 a write on a plant without configuration stores it disabled")
    void newConfigurationIsStoredDisabled() throws Exception {
        Plant plant = createPlant();

        putConfig(plant, """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(stored(plant.getId()).orElseThrow().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("HCF-006 AC2 a write on an enabled configuration keeps it enabled and updates the other fields")
    void enabledConfigurationStaysEnabled() throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD, true);

        putConfig(plant, """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        HuaweiConfig stored = stored(plant.getId()).orElseThrow();
        assertThat(stored.getEnabled()).isTrue();
        assertThat(stored.getUsername()).isEqualTo("newUser");
        assertThat(stored.getPassword()).isEqualTo("newPassword");
        assertThat(stored.getBaseUrl()).isEqualTo(OTHER_BASE_URL);
    }

    @Test
    @DisplayName("HCF-006 AC3 a write on a disabled configuration keeps it disabled")
    void disabledConfigurationStaysDisabled() throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD, false);

        putConfig(plant, """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        HuaweiConfig stored = stored(plant.getId()).orElseThrow();
        assertThat(stored.getEnabled()).isFalse();
        assertThat(stored.getUsername()).isEqualTo("newUser");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "null"})
    @DisplayName("HCF-006 AC4 a body carrying enabled is rejected and changes nothing")
    void enabledInBodyWithConfigIsRejected(String enabled) throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD);

        putConfig(plant, """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s", "enabled": %s}
                """.formatted(OTHER_BASE_URL, enabled))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()))
                .andExpect(jsonPath("$.message").value(containsString("enabled")));

        assertSeedConfigUnchanged(plant.getId(), STORED_PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "null"})
    @DisplayName("HCF-006 AC4 a body carrying enabled is rejected on a plant without configuration and creates none")
    void enabledInBodyWithoutConfigIsRejected(String enabled) throws Exception {
        Plant plant = createPlant();

        putConfig(plant, """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s", "enabled": %s}
                """.formatted(OTHER_BASE_URL, enabled))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("enabled")));

        assertThat(stored(plant.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("HCF-006 AC5 updating the credentials of an enabled configuration keeps it enabled and complete")
    void updatedCredentialsKeepConfigurationEnabledAndComplete(boolean withNewPassword) throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), STORED_PASSWORD, true);
        String body = withNewPassword
                ? """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL)
                : """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL);

        putConfig(plant, body).andExpect(status().isOk());

        HuaweiConfig stored = stored(plant.getId()).orElseThrow();
        assertThat(stored.getEnabled()).isTrue();
        assertThat(stored.getUsername()).isEqualTo("newUser");
        assertThat(stored.getPassword()).isEqualTo(withNewPassword ? "newPassword" : STORED_PASSWORD);
        assertThat(stored.getBaseUrl()).isEqualTo(OTHER_BASE_URL);

        mockMvc.perform(post(String.format(ENABLE_URL_TEMPLATE, plant.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID)))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HCF-006 a write on one plant leaves another plant's enabled untouched")
    void writeDoesNotTouchOtherPlantsEnabled() throws Exception {
        Plant enabledPlant = createPlant();
        Plant disabledPlant = createPlant();
        seedConfig(enabledPlant.getId(), STORED_PASSWORD, true);
        seedConfig(disabledPlant.getId(), STORED_PASSWORD, false);

        putConfig(enabledPlant, """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk());

        HuaweiConfig disabled = stored(disabledPlant.getId()).orElseThrow();
        assertThat(disabled.getEnabled()).isFalse();
        assertThat(disabled.getUsername()).isEqualTo("seedUser");

        putConfig(disabledPlant, """
                {"username": "otherUser", "password": "otherPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk());

        HuaweiConfig enabled = stored(enabledPlant.getId()).orElseThrow();
        assertThat(enabled.getEnabled()).isTrue();
        assertThat(enabled.getUsername()).isEqualTo("newUser");
        assertThat(stored(disabledPlant.getId()).orElseThrow().getEnabled()).isFalse();
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(put(String.format(URL_TEMPLATE, UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {
        mockMvc.perform(put(String.format(URL_TEMPLATE, UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private void assertOmittedPasswordIsRejectedOver(String storedPassword) throws Exception {
        Plant plant = createPlant();
        seedConfig(plant.getId(), storedPassword);

        putConfig(plant, """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.HUAWEI_CONFIG_PASSWORD_REQUIRED.name()));

        assertSeedConfigUnchanged(plant.getId(), storedPassword);
    }

    private Plant createPlant() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);
        return createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }

    private void seedConfig(UUID plantId, String password) {
        seedConfig(plantId, password, true);
    }

    private void seedConfig(UUID plantId, String password, boolean enabled) {
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, new HuaweiConfig.Builder()
                .setUsername("seedUser")
                .setPassword(password)
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build());
    }

    private ResultActions putConfig(Plant plant, String body) throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        return mockMvc.perform(
                        put(String.format(URL_TEMPLATE, plant.getId()))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andDo(print());
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

    private void assertSeedConfigUnchanged(UUID plantId, String storedPassword) {
        HuaweiConfig stored = stored(plantId).orElseThrow();
        assertThat(stored.getPassword()).isEqualTo(storedPassword);
        assertThat(stored.getUsername()).isEqualTo("seedUser");
        assertThat(stored.getBaseUrl()).isEqualTo(HuaweiConfig.DEFAULT_BASE_URL);
        assertThat(stored.getEnabled()).isTrue();
    }
}
