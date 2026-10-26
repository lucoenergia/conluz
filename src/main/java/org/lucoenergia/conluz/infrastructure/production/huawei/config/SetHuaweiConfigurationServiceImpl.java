package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigPasswordRequiredException;
import org.lucoenergia.conluz.domain.production.huawei.config.LockHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfiguration;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.lock.LockPlantRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Service
public class SetHuaweiConfigurationServiceImpl implements SetHuaweiConfigurationService {

    private final LockPlantRepository lockPlantRepository;
    private final LockHuaweiConfigRepository lockHuaweiConfigRepository;
    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    public SetHuaweiConfigurationServiceImpl(LockPlantRepository lockPlantRepository,
                                             LockHuaweiConfigRepository lockHuaweiConfigRepository,
                                             GetHuaweiConfigRepository getHuaweiConfigRepository,
                                             SetHuaweiConfigurationRepository setHuaweiConfigurationRepository) {
        this.lockPlantRepository = lockPlantRepository;
        this.lockHuaweiConfigRepository = lockHuaweiConfigRepository;
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.setHuaweiConfigurationRepository = setHuaweiConfigurationRepository;
    }

    /**
     * The write never changes {@code enabled} (#425): a new configuration starts disabled, and an existing one keeps
     * its stored value, which only enabling and disabling change. The stored password is kept the same way when
     * none is supplied (#399).
     *
     * <p>Both are kept by writing back what was read, so the row is locked before it is read. Without the lock, an
     * enable or disable committed between this read and this write would be reverted by the write; with it, that
     * enable or disable waits for this transaction and applies on top of it.</p>
     *
     * <p>The plant is locked against deletion first (PLT-001), before the configuration row, in the order a plant
     * deletion takes them: a deletion in progress is waited for, and then the plant is not found, instead of a new
     * configuration failing on its foreign key.</p>
     */
    @Override
    public HuaweiConfig setHuaweiConfiguration(UUID plantId, SetHuaweiConfiguration configuration) {
        if (!lockPlantRepository.lockAsReferenced(PlantId.of(plantId))) {
            throw new PlantNotFoundException(PlantId.of(plantId));
        }
        Optional<HuaweiConfig> stored = lockHuaweiConfigRepository.lock(plantId)
                ? getHuaweiConfigRepository.getHuaweiConfig(plantId)
                : Optional.empty();
        String password = configuration.getPassword().orElseGet(() -> storedPassword(stored, plantId));
        HuaweiConfig config = new HuaweiConfig.Builder()
                .setUsername(configuration.getUsername())
                .setPassword(password)
                .setBaseUrl(configuration.getBaseUrl())
                .setEnabled(stored.map(HuaweiConfig::getEnabled).orElse(Boolean.FALSE))
                .build();
        return setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, config);
    }

    /**
     * A row may hold a null or empty password, written before the request body was validated. Keeping it
     * would leave the configuration without credentials, so only a non-empty password can be kept.
     */
    private String storedPassword(Optional<HuaweiConfig> stored, UUID plantId) {
        return stored
                .map(HuaweiConfig::getPassword)
                .filter(StringUtils::isNotEmpty)
                .orElseThrow(() -> new HuaweiConfigPasswordRequiredException(plantId));
    }
}
