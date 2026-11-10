package org.lucoenergia.conluz.infrastructure.production.huawei.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;

@Transactional
class GetHuaweiConfigRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetHuaweiConfigRepository getHuaweiConfigRepository;
    @Autowired
    private SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;

    @Test
    @DisplayName("PIP-001 only a plant whose own configuration is enabled is enabled")
    void onlyAPlantWhoseOwnConfigurationIsEnabledIsEnabled() {
        UUID enabled = createPlant().getId();
        UUID disabled = createPlant().getId();
        UUID withoutConfiguration = createPlant().getId();
        configure(enabled, true);
        configure(disabled, false);

        assertTrue(getHuaweiConfigRepository.isEnabled(enabled));
        assertFalse(getHuaweiConfigRepository.isEnabled(disabled));
        assertFalse(getHuaweiConfigRepository.isEnabled(withoutConfiguration));
        assertFalse(getHuaweiConfigRepository.isEnabled(UUID.randomUUID()));
    }

    @Test
    @DisplayName("PIP-001 disabling an enabled configuration makes the plant not enabled")
    void disablingAnEnabledConfigurationMakesThePlantNotEnabled() {
        UUID plantId = createPlant().getId();
        configure(plantId, true);

        setHuaweiConfigurationRepository.setEnabled(plantId, false);

        assertFalse(getHuaweiConfigRepository.isEnabled(plantId));
    }

    private void configure(UUID plantId, boolean enabled) {
        setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, new HuaweiConfig.Builder()
                .setUsername("user")
                .setPassword("password")
                .setBaseUrl(HuaweiConfig.DEFAULT_BASE_URL)
                .setEnabled(enabled)
                .build());
    }

    private Plant createPlant() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Supply supply = createSupplyRepository.create(SupplyMother.random().build(), UserId.of(user.getId()),
                DEFAULT_COMMUNITY_ID);
        return createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
    }
}
