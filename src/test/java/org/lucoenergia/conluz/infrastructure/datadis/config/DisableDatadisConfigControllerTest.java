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
class DisableDatadisConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/communities/%s/config/datadis/disable";
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
    @DisplayName("DCF-004 AC1 disabling a complete enabled configuration changes only enabled")
    void disablingCompleteConfigurationChangesOnlyEnabled() throws Exception {
        UUID communityId = createCommunity();
        seed(communityId, USERNAME, BASE_URL, PASSWORD, true);

        disable(communityId).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertStored(communityId, USERNAME, BASE_URL, PASSWORD, false);
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
    @DisplayName("DCF-004 AC4 disabling an incomplete enabled configuration succeeds and changes only enabled")
    void disablingIncompleteConfigurationSucceeds(String username, String baseUrl, String password) throws Exception {
        UUID communityId = createCommunity();
        seed(communityId, username, baseUrl, password, true);

        disable(communityId).andExpect(status().isNoContent());

        assertStored(communityId, username, baseUrl, password, false);
    }

    @Test
    @DisplayName("DCF-004 AC5 disabling a community without a configuration is not found and creates none")
    void disablingWithoutConfigurationIsNotFound() throws Exception {
        UUID communityId = createCommunity();

        disable(communityId)
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
    @DisplayName("DCF-004 disabling an already disabled configuration succeeds")
    void disablingDisabledConfigurationSucceeds() throws Exception {
        UUID communityId = createCommunity();
        seed(communityId, USERNAME, BASE_URL, PASSWORD, false);

        disable(communityId).andExpect(status().isNoContent());

        assertStored(communityId, USERNAME, BASE_URL, PASSWORD, false);
    }

    @Test
    @DisplayName("DCF-004 disabling one community's configuration leaves another community's untouched")
    void otherCommunityIsUntouched() throws Exception {
        UUID communityId = createCommunity();
        UUID otherCommunityId = createCommunity();
        seed(communityId, USERNAME, BASE_URL, PASSWORD, true);
        seed(otherCommunityId, "otherUser", BASE_URL, "otherPassword", true);

        disable(communityId).andExpect(status().isNoContent());

        assertStored(communityId, USERNAME, BASE_URL, PASSWORD, false);
        assertStored(otherCommunityId, "otherUser", BASE_URL, "otherPassword", true);
    }

    /**
     * The configuration write runs first and is rejected, so the two answers are compared over the same,
     * unchanged configuration.
     */
    private void assertSameStatusAsConfigurationWrite(UUID communityId, String authHeader, HttpStatus expected)
            throws Exception {
        seed(communityId, USERNAME, BASE_URL, PASSWORD, true);

        MockHttpServletRequestBuilder configurationWrite = put(String.format(CONFIG_URL_TEMPLATE, communityId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "newUser", "password": "newPassword", "baseUrl": "%s", "enabled": false}
                        """.formatted(BASE_URL));
        MockHttpServletRequestBuilder disableRequest = post(String.format(URL_TEMPLATE, communityId));
        if (authHeader != null) {
            configurationWrite.header(HttpHeaders.AUTHORIZATION, authHeader);
            disableRequest.header(HttpHeaders.AUTHORIZATION, authHeader);
        }

        perform(configurationWrite).andExpect(status().is(expected.value()));
        perform(disableRequest).andExpect(status().is(expected.value()));

        assertStored(communityId, USERNAME, BASE_URL, PASSWORD, true);
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

    private ResultActions disable(UUID communityId) throws Exception {
        return perform(post(String.format(URL_TEMPLATE, communityId))
                .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(communityId)));
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
