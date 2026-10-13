package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SetShellyConfigControllerTest extends BaseControllerTest {

    private static final String URL_TEMPLATE = "/api/v1/communities/%s/config/shelly";

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private GetShellyConfigRepository getShellyConfigRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("a valid body stores the configuration")
    void validBodyIsStored() throws Exception {
        Community community = createCommunity();

        putConfig(community.getId(), """
                {"enabled": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(stored(community.getId())).map(ShellyConfig::getEnabled).contains(Boolean.TRUE);
    }

    @Test
    @DisplayName("a missing enabled flag is rejected and stores nothing")
    void missingEnabledIsRejected() throws Exception {
        assertInvalidBodyStoresNothing("{}");
    }

    @Test
    @DisplayName("a null enabled flag is rejected and stores nothing")
    void nullEnabledIsRejected() throws Exception {
        assertInvalidBodyStoresNothing("""
                {"enabled": null}
                """);
    }

    private void assertInvalidBodyStoresNothing(String body) throws Exception {
        Community community = createCommunity();

        putConfig(community.getId(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("enabled")));

        assertThat(stored(community.getId())).isEmpty();
    }

    private Community createCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    private ResultActions putConfig(UUID communityId, String body) throws Exception {
        String authHeader = loginAsCommunityAdmin(communityId);
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
    private Optional<ShellyConfig> stored(UUID communityId) {
        entityManager.flush();
        entityManager.clear();
        return getShellyConfigRepository.getShellyConfig(communityId);
    }
}
