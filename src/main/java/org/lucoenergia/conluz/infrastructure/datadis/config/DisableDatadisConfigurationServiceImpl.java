package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.lucoenergia.conluz.domain.datadis.DisableDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigNotFoundException;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class DisableDatadisConfigurationServiceImpl implements DisableDatadisConfigurationService {

    private final SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    public DisableDatadisConfigurationServiceImpl(SetDatadisConfigurationRepository setDatadisConfigurationRepository) {
        this.setDatadisConfigurationRepository = setDatadisConfigurationRepository;
    }

    /**
     * Never reads the stored configuration: disabling does not depend on what it holds.
     */
    @Override
    public void disable(UUID communityId) {
        if (!setDatadisConfigurationRepository.setEnabled(communityId, false)) {
            throw new DatadisConfigNotFoundException(communityId);
        }
    }
}
