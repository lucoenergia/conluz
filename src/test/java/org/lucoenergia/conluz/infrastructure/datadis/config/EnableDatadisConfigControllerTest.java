package org.lucoenergia.conluz.infrastructure.datadis.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class EnableDatadisConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/communities/%s/config/datadis/enable";
    private static final String CONFIG_URL_TEMPLATE = "/api/v1/communities/%s/config/datadis";

    private static final String USERNAME = "seedUser";
    private static final String PASSWORD = "seedPassword";
    private static final String BASE_URL = DatadisConfig.DEFAULT_BASE_URL;

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    @Autowired
    private GetDatadisConfigRepository getDatadisConfigRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("DCF-005 AC2 enabling a complete disabled configuration changes only enabled")
    void enablingCompleteConfigurationChangesOnlyEnabled() throws Exception {
        UUID communityId = createCommunity();
        seed(communityId, USERNAME, BASE_URL, PASSWORD, false);

        enable(communityId).andExpect(status().isOk()).andExpect(content().string(""));

        assertStored(communityId, USERNAME, BASE_URL, PASSWORD, true);
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
    @DisplayName("DCF-005 AC3 enabling an incomplete configuration is a conflict listing the missing fields, and changes nothing")
    void enablingIncompleteConfigurationIsConflict(String username, String baseUrl, String password,
                                                   String missingFields) throws Exception {
        UUID communityId = createCommunity();
        seed(communityId, username, baseUrl, password, false);

        enable(communityId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.DATADIS_CONFIG_INCOMPLETE.name()))
                .andExpect(jsonPath("$.errors[0].params.missingFields").value(missingFields));

        assertStored(communityId, username, baseUrl, password, false);
    }

    @Test
    @DisplayName("DCF-005 AC5 enabling a community without a configuration is not found and creates none")
    void enablingWithoutConfigurationIsNotFound() throws Exception {
        UUID communityId = createCommunity();

        enable(communityId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.DATADIS_CONFIG_NOT_FOUND.name()));

        assertThat(stored(communityId)).isEmpty();
    }

    @Test
    @DisplayName("AC6 a community id that is not a UUID is rejected")
    void malformedCommunityIdIsRejected() throws Exception {
        perform(post(String.format(URL_TEMPLATE, "not-a-uuid"))
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(createCommunity())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    @DisplayName("AC7 without a token the request is unauthorized, like the configuration write, and changes nothing")
    void withoutTokenIsUnauthorized() throws Exception {
        assertSameStatusAsConfigurationWrite(createCommunity(), null, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AC7 a caller who is not a member of the community gets not-found, like the configuration write, and changes nothing")
    void nonMemberGetsNotFound() throws Exception {
        assertSameStatusAsConfigurationWrite(createCommunity(), loginAsPartner(), HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("AC7 a member who is not a community admin is forbidden, like the configuration write, and changes nothing")
    void memberWithoutAdminRoleIsForbidden() throws Exception {
        UUID communityId = createCommunity();
        assertSameStatusAsConfigurationWrite(communityId, loginAsCommunityMember(communityId), HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AC7 a platform admin who is not a member is forbidden, like the configuration write, and changes nothing")
    void platformAdminWithoutMembershipIsForbidden() throws Exception {
        assertSameStatusAsConfigurationWrite(createCommunity(), loginAsDefaultPlatformAdmin(), HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("DCF-005 enabling an already enabled complete configuration succeeds")
    void enablingEnabledConfigurationSucceeds() throws Exception {
        UUID communityId = createCommunity();
        seed(communityId, USERNAME, BASE_URL, PASSWORD, true);

        enable(communityId).andExpect(status().isOk());

        assertStored(communityId, USERNAME, BASE_URL, PASSWORD, true);
    }

    @Test
    @DisplayName("DCF-005 enabling one community's configuration leaves another community's untouched")
    void otherCommunityIsUntouched() throws Exception {
        UUID communityId = createCommunity();
        UUID otherCommunityId = createCommunity();
        seed(communityId, USERNAME, BASE_URL, PASSWORD, false);
        seed(otherCommunityId, "otherUser", BASE_URL, "otherPassword", false);

        enable(communityId).andExpect(status().isOk());

        assertStored(communityId, USERNAME, BASE_URL, PASSWORD, true);
        assertStored(otherCommunityId, "otherUser", BASE_URL, "otherPassword", false);
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#rejected")
    @DisplayName("DCF-005 a base URL the configuration write rejects is reported missing when enabling")
    void baseUrlRejectedByTheWriteIsMissing(String baseUrl) throws Exception {
        UUID writtenCommunityId = createCommunity();
        putConfig(writtenCommunityId, baseUrl).andExpect(status().isBadRequest());

        UUID communityId = createCommunity();
        seed(communityId, USERNAME, baseUrl, PASSWORD, false);

        enable(communityId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value(RestErrorCode.DATADIS_CONFIG_INCOMPLETE.name()))
                .andExpect(jsonPath("$.errors[0].params.missingFields").value("baseUrl"));
        assertStored(communityId, USERNAME, baseUrl, PASSWORD, false);
    }

    @ParameterizedTest
    @MethodSource("org.lucoenergia.conluz.domain.shared.config.BaseUrlFixtures#accepted")
    @DisplayName("DCF-005 a base URL the configuration write accepts is not reported missing when enabling")
    void baseUrlAcceptedByTheWriteIsNotMissing(String baseUrl) throws Exception {
        UUID writtenCommunityId = createCommunity();
        putConfig(writtenCommunityId, baseUrl).andExpect(status().isOk());

        UUID communityId = createCommunity();
        seed(communityId, USERNAME, baseUrl, PASSWORD, false);

        enable(communityId).andExpect(status().isOk());
        assertStored(communityId, USERNAME, baseUrl, PASSWORD, true);
    }

    /**
     * The configuration write runs first and is rejected, so the two answers are compared over the same,
     * unchanged configuration.
     */
    private void assertSameStatusAsConfigurationWrite(UUID communityId, String authHeader, HttpStatus expected)
            throws Exception {
        seed(communityId, USERNAME, BASE_URL, PASSWORD, false);

        MockHttpServletRequestBuilder configurationWrite = put(String.format(CONFIG_URL_TEMPLATE, communityId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "newUser", "password": "newPassword", "baseUrl": "%s", "enabled": true}
                        """.formatted(BASE_URL));
        MockHttpServletRequestBuilder enableRequest = post(String.format(URL_TEMPLATE, communityId));
        if (authHeader != null) {
            configurationWrite.header(HttpHeaders.AUTHORIZATION, authHeader);
            enableRequest.header(HttpHeaders.AUTHORIZATION, authHeader);
        }

        perform(configurationWrite).andExpect(status().is(expected.value()));
        perform(enableRequest).andExpect(status().is(expected.value()));

        assertStored(communityId, USERNAME, BASE_URL, PASSWORD, false);
    }

    private UUID createCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build()).getId();
    }

    /**
     * Through the repository's upsert rather than the configuration write, which validates its body: rows stored
     * before that validation existed may hold any of these values.
     */
    private void seed(UUID communityId, String username, String baseUrl, String password, boolean enabled) {
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername(username)
                .setPassword(password)
                .setBaseUrl(baseUrl)
                .setEnabled(enabled)
                .build());
    }

    private ResultActions enable(UUID communityId) throws Exception {
        return perform(post(String.format(URL_TEMPLATE, communityId))
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(communityId)));
    }

    private ResultActions putConfig(UUID communityId, String baseUrl) throws Exception {
        return perform(put(String.format(CONFIG_URL_TEMPLATE, communityId))
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(communityId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ConfigureDatadisBody(USERNAME, PASSWORD, baseUrl, Boolean.TRUE))));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andDo(print());
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

    private void assertStored(UUID communityId, String username, String baseUrl, String password, boolean enabled) {
        DatadisConfig stored = stored(communityId).orElseThrow();
        assertThat(stored.getUsername()).isEqualTo(username);
        assertThat(stored.getBaseUrl()).isEqualTo(baseUrl);
        assertThat(stored.getPassword()).isEqualTo(password);
        assertThat(stored.getEnabled()).isEqualTo(enabled);
    }
}
