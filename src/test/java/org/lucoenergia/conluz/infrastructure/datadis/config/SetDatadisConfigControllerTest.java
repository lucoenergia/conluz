package org.lucoenergia.conluz.infrastructure.datadis.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
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
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SetDatadisConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/communities/%s/config/datadis";
    private static final String ENABLE_URL_TEMPLATE = "/api/v1/communities/%s/config/datadis/enable";

    private static final String STORED_PASSWORD = "storedPassword";
    private static final String OTHER_BASE_URL = "https://datadis.example.com";

    @Autowired
    private DatadisConfigRepository repository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    @Autowired
    private GetDatadisConfigRepository getDatadisConfigRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void testOverrideConfigureDatadis() throws Exception {
        setDatadisConfigurationRepository.setDatadisConfiguration(DEFAULT_COMMUNITY_ID, new DatadisConfig.Builder()
                .setUsername("testUsername")
                .setPassword("testPassword")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(Boolean.FALSE)
                .build());

        ConfigureDatadisBody body = new ConfigureDatadisBody("testUsernameNew", "testPasswordNew",
                DatadisConfig.DEFAULT_BASE_URL);

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(
                put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testUsernameNew"))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.baseUrl").value(DatadisConfig.DEFAULT_BASE_URL))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void testNewConfigureDatadis() throws Exception {
        ConfigureDatadisBody body = new ConfigureDatadisBody("testUser", "testPass",
                DatadisConfig.DEFAULT_BASE_URL);

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(
                        put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testUser"))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andExpect(jsonPath("$.baseUrl").value(DatadisConfig.DEFAULT_BASE_URL))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("DCF-001 omitting the password updates the other fields and keeps the stored password")
    void omittedPasswordKeepsStoredOne() throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD);

        putConfig(community.getId(), """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordSet").value(true));

        DatadisConfig stored = stored(community.getId()).orElseThrow();
        assertThat(stored.getPassword()).isEqualTo(STORED_PASSWORD);
        assertThat(stored.getUsername()).isEqualTo("newUser");
        assertThat(stored.getBaseUrl()).isEqualTo(OTHER_BASE_URL);
        assertThat(stored.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("DCF-001 omitting the password on one community leaves another community's configuration untouched")
    void omittedPasswordDoesNotTouchOtherCommunity() throws Exception {
        Community community = createCommunity();
        Community other = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD);
        seedConfig(other.getId(), "otherPassword");

        putConfig(community.getId(), """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk());

        assertSeedConfigUnchanged(other.getId(), "otherPassword");
    }

    @Test
    @DisplayName("a non-empty password replaces the stored one")
    void newPasswordReplacesStoredOne() throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD);

        putConfig(community.getId(), """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk());

        assertThat(stored(community.getId()).orElseThrow().getPassword()).isEqualTo("newPassword");
    }

    @Test
    @DisplayName("DCF-002 omitting the password on a community without configuration is rejected and stores nothing")
    void omittedPasswordWithoutConfigIsRejected() throws Exception {
        Community community = createCommunity();

        putConfig(community.getId(), """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(DatadisConfig.DEFAULT_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.DATADIS_CONFIG_PASSWORD_REQUIRED.name()));

        assertThat(stored(community.getId())).isEmpty();
    }

    @Test
    @DisplayName("DCF-002 omitting the password when the stored password is null is rejected and changes nothing")
    void omittedPasswordWithStoredNullPasswordIsRejected() throws Exception {
        assertOmittedPasswordIsRejectedOver(null);
    }

    @Test
    @DisplayName("DCF-002 omitting the password when the stored password is empty is rejected and changes nothing")
    void omittedPasswordWithStoredEmptyPasswordIsRejected() throws Exception {
        assertOmittedPasswordIsRejectedOver("");
    }

    @Test
    @DisplayName("DCF-003 an explicit null password is rejected on a community with a configuration")
    void nullPasswordWithConfigIsRejected() throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD);

        putConfig(community.getId(), """
                {"username": "newUser", "password": null, "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()))
                .andExpect(jsonPath("$.message").value(containsString("password")));

        assertSeedConfigUnchanged(community.getId(), STORED_PASSWORD);
    }

    @Test
    @DisplayName("DCF-003 an explicit null password is rejected on a community without configuration")
    void nullPasswordWithoutConfigIsRejected() throws Exception {
        Community community = createCommunity();

        putConfig(community.getId(), """
                {"username": "newUser", "password": null, "baseUrl": "%s"}
                """.formatted(DatadisConfig.DEFAULT_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()));

        assertThat(stored(community.getId())).isEmpty();
    }

    @Test
    @DisplayName("an empty password is rejected on a community with a configuration")
    void emptyPasswordWithConfigIsRejected() throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD);

        putConfig(community.getId(), """
                {"username": "newUser", "password": "", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("password")));

        assertSeedConfigUnchanged(community.getId(), STORED_PASSWORD);
    }

    @Test
    @DisplayName("an empty password is rejected on a community without configuration")
    void emptyPasswordWithoutConfigIsRejected() throws Exception {
        Community community = createCommunity();

        putConfig(community.getId(), """
                {"username": "newUser", "password": "", "baseUrl": "%s"}
                """.formatted(DatadisConfig.DEFAULT_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("password")));

        assertThat(stored(community.getId())).isEmpty();
    }

    @Test
    @DisplayName("a blank username is rejected and stores nothing")
    void blankUsernameIsRejected() throws Exception {
        assertInvalidBodyStoresNothing("""
                {"username": " ", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(DatadisConfig.DEFAULT_BASE_URL), "username");
    }

    @Test
    @DisplayName("a missing username is rejected and stores nothing")
    void missingUsernameIsRejected() throws Exception {
        assertInvalidBodyStoresNothing("""
                {"password": "newPassword", "baseUrl": "%s"}
                """.formatted(DatadisConfig.DEFAULT_BASE_URL), "username");
    }

    @Test
    @DisplayName("a blank base URL is rejected and stores nothing")
    void blankBaseUrlIsRejected() throws Exception {
        assertInvalidBodyStoresNothing("""
                {"username": "newUser", "password": "newPassword", "baseUrl": ""}
                """, "baseUrl");
    }

    @Test
    @DisplayName("a malformed base URL is rejected and stores nothing")
    void malformedBaseUrlIsRejected() throws Exception {
        assertInvalidBodyStoresNothing("""
                {"username": "newUser", "password": "newPassword", "baseUrl": "not a url"}
                """, "baseUrl");
    }

    @Test
    @DisplayName("a member who is not a community admin is forbidden and changes nothing")
    void memberWithoutAdminRoleIsForbidden() throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD);
        String authHeader = loginAsCommunityMember(community.getId());

        putConfigAs(authHeader, community.getId(), validBody())
                .andExpect(status().isForbidden());

        assertSeedConfigUnchanged(community.getId(), STORED_PASSWORD);
    }

    @Test
    @DisplayName("a caller who is not a member of the community gets not-found and changes nothing")
    void nonMemberGetsNotFound() throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD);
        String authHeader = loginAsPartner();

        putConfigAs(authHeader, community.getId(), validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));

        assertSeedConfigUnchanged(community.getId(), STORED_PASSWORD);
    }

    @Test
    @DisplayName("DCF-006 AC1 a write on a community without configuration stores it disabled")
    void newConfigurationIsStoredDisabled() throws Exception {
        Community community = createCommunity();

        putConfig(community.getId(), """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(stored(community.getId()).orElseThrow().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("DCF-006 AC2 a write on an enabled configuration keeps it enabled and updates the other fields")
    void enabledConfigurationStaysEnabled() throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD, true);

        putConfig(community.getId(), """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        DatadisConfig stored = stored(community.getId()).orElseThrow();
        assertThat(stored.getEnabled()).isTrue();
        assertThat(stored.getUsername()).isEqualTo("newUser");
        assertThat(stored.getPassword()).isEqualTo("newPassword");
        assertThat(stored.getBaseUrl()).isEqualTo(OTHER_BASE_URL);
    }

    @Test
    @DisplayName("DCF-006 AC3 a write on a disabled configuration keeps it disabled")
    void disabledConfigurationStaysDisabled() throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD, false);

        putConfig(community.getId(), """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        DatadisConfig stored = stored(community.getId()).orElseThrow();
        assertThat(stored.getEnabled()).isFalse();
        assertThat(stored.getUsername()).isEqualTo("newUser");
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "null"})
    @DisplayName("DCF-006 AC4 a body carrying enabled is rejected and changes nothing")
    void enabledInBodyWithConfigIsRejected(String enabled) throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD);

        putConfig(community.getId(), """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s", "enabled": %s}
                """.formatted(OTHER_BASE_URL, enabled))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(nullValue()))
                .andExpect(jsonPath("$.message").value(containsString("enabled")));

        assertSeedConfigUnchanged(community.getId(), STORED_PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "null"})
    @DisplayName("DCF-006 AC4 a body carrying enabled is rejected on a community without configuration and creates none")
    void enabledInBodyWithoutConfigIsRejected(String enabled) throws Exception {
        Community community = createCommunity();

        putConfig(community.getId(), """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s", "enabled": %s}
                """.formatted(OTHER_BASE_URL, enabled))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("enabled")));

        assertThat(stored(community.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("DCF-006 AC5 updating the credentials of an enabled configuration keeps it enabled and complete")
    void updatedCredentialsKeepConfigurationEnabledAndComplete(boolean withNewPassword) throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), STORED_PASSWORD, true);
        String body = withNewPassword
                ? """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL)
                : """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL);

        putConfig(community.getId(), body).andExpect(status().isOk());

        DatadisConfig stored = stored(community.getId()).orElseThrow();
        assertThat(stored.getEnabled()).isTrue();
        assertThat(stored.getUsername()).isEqualTo("newUser");
        assertThat(stored.getPassword()).isEqualTo(withNewPassword ? "newPassword" : STORED_PASSWORD);
        assertThat(stored.getBaseUrl()).isEqualTo(OTHER_BASE_URL);

        mockMvc.perform(post(String.format(ENABLE_URL_TEMPLATE, community.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId())))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DCF-006 a write on one community leaves another community's enabled untouched")
    void writeDoesNotTouchOtherCommunitysEnabled() throws Exception {
        Community enabledCommunity = createCommunity();
        Community disabledCommunity = createCommunity();
        seedConfig(enabledCommunity.getId(), STORED_PASSWORD, true);
        seedConfig(disabledCommunity.getId(), STORED_PASSWORD, false);

        putConfig(enabledCommunity.getId(), """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk());

        DatadisConfig disabled = stored(disabledCommunity.getId()).orElseThrow();
        assertThat(disabled.getEnabled()).isFalse();
        assertThat(disabled.getUsername()).isEqualTo("seedUser");

        putConfig(disabledCommunity.getId(), """
                {"username": "otherUser", "password": "otherPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isOk());

        DatadisConfig enabled = stored(enabledCommunity.getId()).orElseThrow();
        assertThat(enabled.getEnabled()).isTrue();
        assertThat(enabled.getUsername()).isEqualTo("newUser");
        assertThat(stored(disabledCommunity.getId()).orElseThrow().getEnabled()).isFalse();
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
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
        mockMvc.perform(put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {
        String authHeader = loginAsPartner();

        ConfigureDatadisBody body = new ConfigureDatadisBody("testUsername", "testPassword",
                DatadisConfig.DEFAULT_BASE_URL);

        mockMvc.perform(put(String.format(URL_TEMPLATE, DEFAULT_COMMUNITY_ID))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    private void assertOmittedPasswordIsRejectedOver(String storedPassword) throws Exception {
        Community community = createCommunity();
        seedConfig(community.getId(), storedPassword);

        putConfig(community.getId(), """
                {"username": "newUser", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.DATADIS_CONFIG_PASSWORD_REQUIRED.name()));

        assertSeedConfigUnchanged(community.getId(), storedPassword);
    }

    private void assertInvalidBodyStoresNothing(String body, String field) throws Exception {
        Community community = createCommunity();

        putConfig(community.getId(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(field)));

        assertThat(stored(community.getId())).isEmpty();
    }

    private Community createCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    private void seedConfig(UUID communityId, String password) {
        seedConfig(communityId, password, true);
    }

    private void seedConfig(UUID communityId, String password, boolean enabled) {
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername("seedUser")
                .setPassword(password)
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build());
    }

    private static String validBody() {
        return """
                {"username": "newUser", "password": "newPassword", "baseUrl": "%s"}
                """.formatted(OTHER_BASE_URL);
    }

    private ResultActions putConfig(UUID communityId, String body) throws Exception {
        return putConfigAs(loginAsCommunityAdmin(communityId), communityId, body);
    }

    private ResultActions putConfigAs(String authHeader, UUID communityId, String body) throws Exception {
        return mockMvc.perform(
                        put(String.format(URL_TEMPLATE, communityId))
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andDo(print());
    }

    /**
     * Reads the row back from the database rather than from the persistence context, so an assertion sees
     * what the request wrote, not what a managed entity holds in memory.
     */
    private Optional<DatadisConfig> stored(UUID communityId) {
        entityManager.flush();
        entityManager.clear();
        return getDatadisConfigRepository.findByCommunityId(communityId);
    }

    private void assertSeedConfigUnchanged(UUID communityId, String storedPassword) {
        DatadisConfig stored = stored(communityId).orElseThrow();
        assertThat(stored.getPassword()).isEqualTo(storedPassword);
        assertThat(stored.getUsername()).isEqualTo("seedUser");
        assertThat(stored.getBaseUrl()).isEqualTo(DatadisConfig.DEFAULT_BASE_URL);
        assertThat(stored.getEnabled()).isTrue();
    }
}
