package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiBaseUrlValidity;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigIncompleteException;
import org.lucoenergia.conluz.domain.production.huawei.config.HuaweiConfigNotFoundException;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationEnabledService;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.shared.config.ConnectionConfigCompleteness;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional
@Service
public class SetHuaweiConfigurationEnabledServiceImpl implements SetHuaweiConfigurationEnabledService {

    private final GetHuaweiConfigRepository getHuaweiConfigRepository;
    private final SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;
    private final HuaweiBaseUrlValidity baseUrlValidity;

    public SetHuaweiConfigurationEnabledServiceImpl(GetHuaweiConfigRepository getHuaweiConfigRepository,
                                                    SetHuaweiConfigurationRepository setHuaweiConfigurationRepository,
                                                    HuaweiBaseUrlValidity baseUrlValidity) {
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
        this.setHuaweiConfigurationRepository = setHuaweiConfigurationRepository;
        this.baseUrlValidity = baseUrlValidity;
    }

    /**
     * Enabling reads the stored configuration in the same transaction as the write, so the data found complete
     * is the data enabled.
     */
    @Override
    public void setEnabled(UUID plantId, boolean enabled) {
        if (enabled) {
            assertComplete(plantId);
        }
        if (!setHuaweiConfigurationRepository.setEnabled(plantId, enabled)) {
            throw new HuaweiConfigNotFoundException(plantId);
        }
    }

    private void assertComplete(UUID plantId) {
        HuaweiConfig config = getHuaweiConfigRepository.getHuaweiConfig(plantId)
                .orElseThrow(() -> new HuaweiConfigNotFoundException(plantId));
        List<String> missingFields = ConnectionConfigCompleteness.missingFields(
                config.getUsername(), config.getBaseUrl(), config.getPassword(), baseUrlValidity);
        if (!missingFields.isEmpty()) {
            throw new HuaweiConfigIncompleteException(plantId, missingFields);
        }
    }
}
