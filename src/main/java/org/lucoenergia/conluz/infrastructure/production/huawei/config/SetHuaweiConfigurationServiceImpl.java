package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigPasswordRequiredException;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationCommand;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class SetHuaweiConfigurationServiceImpl implements SetHuaweiConfigurationService {

    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    public SetHuaweiConfigurationServiceImpl(GetHuaweiConfigRepository getHuaweiConfigRepository,
                                             SetHuaweiConfigurationRepository setHuaweiConfigurationRepository) {
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.setHuaweiConfigurationRepository = setHuaweiConfigurationRepository;
    }

    /**
     * The stored password is read in the same transaction as the upsert, so the password kept is the one
     * this write replaces.
     */
    @Override
    public HuaweiConfig setHuaweiConfiguration(UUID plantId, SetHuaweiConfigurationCommand command) {
        String password = command.password().orElseGet(() -> storedPassword(plantId));
        HuaweiConfig config = new HuaweiConfig.Builder()
                .setUsername(command.username())
                .setPassword(password)
                .setBaseUrl(command.baseUrl())
                .setEnabled(command.enabled())
                .build();
        return setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, config);
    }

    /**
     * A row may hold a null or empty password, written before the request body was validated. Keeping it
     * would leave the configuration without credentials, so only a non-empty password can be kept.
     */
    private String storedPassword(UUID plantId) {
        return getHuaweiConfigRepository.getHuaweiConfig(plantId)
                .map(HuaweiConfig::getPassword)
                .filter(StringUtils::isNotEmpty)
                .orElseThrow(() -> new HuaweiConfigPasswordRequiredException(plantId));
    }
}
