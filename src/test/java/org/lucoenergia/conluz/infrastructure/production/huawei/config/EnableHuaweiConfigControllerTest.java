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
class EnableHuaweiConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/plants/%s/production/huawei/config/enable";
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
    @DisplayName("HCF-005 AC2 enabling a complete disabled configuration changes only enabled")
    void enablingCompleteConfigurationChangesOnlyEnabled() throws Exception {
        UUID plantId = createPlant();
        seed(plantId, USERNAME, BASE_URL, PASSWORD, false);

        enable(plantId).andExpect(status().isOk()).andExpect(content().string(""));

        assertStored(plantId, USERNAME, BASE_URL, PASSWORD, true);
    }

    static Stream<Arguments> incompleteConfigurations() {
        return Stream.of(
                Arguments.of("   ", BASE_URL, PASSWORD, "username"),
                Arguments.of(null, BASE_URL, PASSWORD, "username"),
                Arguments.of(USERNAME, "not-a-url", PASSWORD, "baseUrl"),
                Arguments.of(USERNAME, " ", PASSWORD, "baseUrl"),
                Arguments.of(USERNAME, BASE_URL, null, "password"),
                Arguments.of(USERNAME, BASE_URL, "", "password"),
                Arguments.of(" ", "not-a-url", null, "username,baseUrl,password"),
                Arguments.of(USERNAME, "not-a-url", "", "baseUrl,password")
        );
    }

    @ParameterizedTest
    @MethodSource("incompleteConfigurations")
    @DisplayName("HCF-005 AC3 enabling an incomplete configuration is a conflict listing the missing fields, and changes nothing")
    void enablingIncompleteConfigurationIsConflict(String username, String baseUrl, String password,
                                                   String missingFields) throws Exception {
        UUID plantId = createPlant();
        seed(plantId, username, baseUrl, password, false);

        enable(plantId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.HUAWEI_CONFIG_INCOMPLETE.name()))
                .andExpect(jsonPath("$.errors[0].params.missingFields").value(missingFields));

        assertStored(plantId, username, baseUrl, password, false);
    }

    @Test
    @DisplayName("HCF-005 AC5 enabling a plant without a configuration is not found and creates none")
    void enablingWithoutConfigurationIsNotFound() throws Exception {
        UUID plantId = createPlant();

        enable(plantId)
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
    @DisplayName("HCF-005 enabling an already enabled complete configuration succeeds")
    void enablingEnabledConfigurationSucceeds() throws Exception {
        UUID plantId = createPlant();
        seed(plantId, USERNAME, BASE_URL, PASSWORD, true);

        enable(plantId).andExpect(status().isOk());

        assertStored(plantId, USERNAME, BASE_URL, PASSWORD, true);
    }

    @Test
    @DisplayName("HCF-005 enabling one plant's configuration leaves another plant's untouched")
    void otherPlantIsUntouched() throws Exception {
        UUID plantId = createPlant();
        UUID otherPlantId = createPlant();
        seed(plantId, USERNAME, BASE_URL, PASSWORD, false);
        seed(otherPlantId, "otherUser", BASE_URL, "otherPassword", false);

        enable(plantId).andExpect(status().isOk());

        assertStored(plantId, USERNAME, BASE_URL, PASSWORD, true);
        assertStored(otherPlantId, "otherUser", BASE_URL, "otherPassword", false);
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#rejected")
    @DisplayName("HCF-005 a base URL the configuration write rejects is reported missing when enabling")
    void baseUrlRejectedByTheWriteIsMissing(String baseUrl) throws Exception {
        UUID writtenPlantId = createPlant();
        putConfig(writtenPlantId, baseUrl).andExpect(status().isBadRequest());

        UUID plantId = createPlant();
        seed(plantId, USERNAME, baseUrl, PASSWORD, false);

        enable(plantId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.HUAWEI_CONFIG_INCOMPLETE.name()))
                .andExpect(jsonPath("$.errors[0].params.missingFields").value("baseUrl"));
        assertStored(plantId, USERNAME, baseUrl, PASSWORD, false);
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#accepted")
    @DisplayName("HCF-005 a base URL the configuration write accepts is not reported missing when enabling")
    void baseUrlAcceptedByTheWriteIsNotMissing(String baseUrl) throws Exception {
        UUID writtenPlantId = createPlant();
        putConfig(writtenPlantId, baseUrl).andExpect(status().isOk());

        UUID plantId = createPlant();
        seed(plantId, USERNAME, baseUrl, PASSWORD, false);

        enable(plantId).andExpect(status().isOk());
        assertStored(plantId, USERNAME, baseUrl, PASSWORD, true);
    }

    /**
     * The configuration write runs first and is rejected, so the two answers are compared over the same,
     * unchanged configuration.
     */
    private void assertSameStatusAsConfigurationWrite(String authHeader, HttpStatus expected) throws Exception {
        UUID plantId = createPlant();
        seed(plantId, USERNAME, BASE_URL, PASSWORD, false);

        MockHttpServletRequestBuilder configurationWrite = put(String.format(CONFIG_URL_TEMPLATE, plantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                        """.formatted(BASE_URL));
        MockHttpServletRequestBuilder enableRequest = post(String.format(URL_TEMPLATE, plantId));
        if (authHeader != null) {
            configurationWrite.header(HttpHeaders.AUTHORIZATION, authHeader);
            enableRequest.header(HttpHeaders.AUTHORIZATION, authHeader);
        }

        perform(configurationWrite).andExpect(status().is(expected.value()));
        perform(enableRequest).andExpect(status().is(expected.value()));

        assertStored(plantId, USERNAME, BASE_URL, PASSWORD, false);
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

    private ResultActions enable(UUID plantId) throws Exception {
        return perform(post(String.format(URL_TEMPLATE, plantId))
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID)));
    }

    private ResultActions putConfig(UUID plantId, String baseUrl) throws Exception {
        return perform(put(String.format(CONFIG_URL_TEMPLATE, plantId))
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ConfigureHuaweiBody(USERNAME, PASSWORD, baseUrl))));
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
