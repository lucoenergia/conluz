package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.config.DisableHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigNotFoundException;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class DisableHuaweiConfigurationServiceImpl implements DisableHuaweiConfigurationService {

    private final SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    public DisableHuaweiConfigurationServiceImpl(SetHuaweiConfigurationRepository setHuaweiConfigurationRepository) {
        this.setHuaweiConfigurationRepository = setHuaweiConfigurationRepository;
    }

    /**
     * Never reads the stored configuration: disabling does not depend on what it holds.
     */
    @Override
    public void disable(UUID plantId) {
        if (!setHuaweiConfigurationRepository.setEnabled(plantId, false)) {
            throw new HuaweiConfigNotFoundException(plantId);
        }
    }
}
