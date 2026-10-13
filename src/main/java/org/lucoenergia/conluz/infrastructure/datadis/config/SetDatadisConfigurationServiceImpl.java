package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigPasswordRequiredException;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfiguration;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class SetDatadisConfigurationServiceImpl implements SetDatadisConfigurationService {

    private final GetDatadisConfigRepository getDatadisConfigRepository;
    private final SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    public SetDatadisConfigurationServiceImpl(GetDatadisConfigRepository getDatadisConfigRepository,
                                              SetDatadisConfigurationRepository setDatadisConfigurationRepository) {
        this.getDatadisConfigRepository = getDatadisConfigRepository;
        this.setDatadisConfigurationRepository = setDatadisConfigurationRepository;
    }

    /**
     * The stored password is read in the same transaction as the upsert, so the password kept is the one
     * this write replaces.
     */
    @Override
    public DatadisConfig setDatadisConfiguration(UUID communityId, SetDatadisConfiguration configuration) {
        String password = configuration.getPassword().orElseGet(() -> storedPassword(communityId));
        DatadisConfig config = new DatadisConfig.Builder()
                .setUsername(configuration.getUsername())
                .setPassword(password)
                .setBaseUrl(configuration.getBaseUrl())
                .setEnabled(configuration.getEnabled())
                .build();
        return setDatadisConfigurationRepository.setDatadisConfiguration(communityId, config);
    }

    /**
     * A row may hold a null or empty password, written before the request body was validated. Keeping it
     * would leave the configuration without credentials, so only a non-empty password can be kept.
     */
    private String storedPassword(UUID communityId) {
        return getDatadisConfigRepository.findByCommunityId(communityId)
                .map(DatadisConfig::getPassword)
                .filter(StringUtils::isNotEmpty)
                .orElseThrow(() -> new DatadisConfigPasswordRequiredException(communityId));
    }
}
