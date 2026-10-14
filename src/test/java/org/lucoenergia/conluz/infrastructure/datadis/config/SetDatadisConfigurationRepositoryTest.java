package org.lucoenergia.conluz.infrastructure.datadis.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class SetDatadisConfigurationRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    @Autowired
    private GetDatadisConfigRepository getDatadisConfigRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("DCF-004 setEnabled writes only enabled, leaving the other fields as stored")
    void setEnabledWritesOnlyEnabled(boolean enabled) {
        UUID communityId = createCommunity();
        seed(communityId, !enabled);

        boolean found = setDatadisConfigurationRepository.setEnabled(communityId, enabled);

        assertThat(found).isTrue();
        DatadisConfig stored = stored(communityId);
        assertThat(stored.getEnabled()).isEqualTo(enabled);
        assertThat(stored.getUsername()).isEqualTo("seedUser");
        assertThat(stored.getPassword()).isEqualTo("seedPassword");
        assertThat(stored.getBaseUrl()).isEqualTo(DatadisConfig.DEFAULT_BASE_URL);
    }

    @Test
    @DisplayName("DCF-004 setEnabled leaves another community's configuration untouched")
    void setEnabledTouchesOnlyTheCommunity() {
        UUID communityId = createCommunity();
        UUID otherCommunityId = createCommunity();
        seed(communityId, true);
        seed(otherCommunityId, true);

        setDatadisConfigurationRepository.setEnabled(communityId, false);

        assertThat(stored(communityId).getEnabled()).isFalse();
        assertThat(stored(otherCommunityId).getEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("DCF-004 setEnabled reports a community without a configuration and creates none")
    void setEnabledWithoutConfiguration(boolean enabled) {
        UUID communityId = createCommunity();

        boolean found = setDatadisConfigurationRepository.setEnabled(communityId, enabled);

        assertThat(found).isFalse();
        entityManager.flush();
        entityManager.clear();
        assertThat(getDatadisConfigRepository.findByCommunityId(communityId)).isEmpty();
    }

    private UUID createCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build()).getId();
    }

    private void seed(UUID communityId, boolean enabled) {
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername("seedUser")
                .setPassword("seedPassword")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build());
    }

    private DatadisConfig stored(UUID communityId) {
        entityManager.flush();
        entityManager.clear();
        return getDatadisConfigRepository.findByCommunityId(communityId).orElseThrow();
    }
}
