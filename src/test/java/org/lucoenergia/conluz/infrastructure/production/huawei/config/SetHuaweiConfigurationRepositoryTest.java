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
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

@Transactional
class SetHuaweiConfigurationRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    @Autowired
    private GetHuaweiConfigRepository getHuaweiConfigRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("HCF-004 setEnabled writes only enabled, leaving the other fields as stored")
    void setEnabledWritesOnlyEnabled(boolean enabled) {
        UUID plantId = createPlant().getId();
        seed(plantId, !enabled);

        boolean found = setHuaweiConfigurationRepository.setEnabled(plantId, enabled);

        assertThat(found).isTrue();
        HuaweiConfig stored = stored(plantId);
        assertThat(stored.getEnabled()).isEqualTo(enabled);
        assertThat(stored.getUsername()).isEqualTo("seedUser");
        assertThat(stored.getPassword()).isEqualTo("seedPassword");
        assertThat(stored.getBaseUrl()).isEqualTo(HuaweiConfig.DEFAULT_BASE_URL);
    }

    @Test
    @DisplayName("HCF-004 setEnabled leaves another plant's configuration untouched")
    void setEnabledTouchesOnlyThePlant() {
        UUID plantId = createPlant().getId();
        UUID otherPlantId = createPlant().getId();
        seed(plantId, true);
        seed(otherPlantId, true);

        setHuaweiConfigurationRepository.setEnabled(plantId, false);

        assertThat(stored(plantId).getEnabled()).isFalse();
        assertThat(stored(otherPlantId).getEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("HCF-004 setEnabled reports a plant without a configuration and creates none")
    void setEnabledWithoutConfiguration(boolean enabled) {
        UUID plantId = createPlant().getId();

        boolean found = setHuaweiConfigurationRepository.setEnabled(plantId, enabled);

        assertThat(found).isFalse();
        entityManager.flush();
        entityManager.clear();
        assertThat(getHuaweiConfigRepository.getHuaweiConfig(plantId)).isEmpty();
    }

    private Plant createPlant() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);
        return createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }

    private void seed(UUID plantId, boolean enabled) {
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, new HuaweiConfig.Builder()
                .setUsername("seedUser")
                .setPassword("seedPassword")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build());
    }

    private HuaweiConfig stored(UUID plantId) {
        entityManager.flush();
        entityManager.clear();
        return getHuaweiConfigRepository.getHuaweiConfig(plantId).orElseThrow();
    }
}
