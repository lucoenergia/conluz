package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigPasswordRequiredException;
import org.lucoenergia.conluz.domain.datadis.LockDatadisConfigRepository;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfiguration;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Service
public class SetDatadisConfigurationServiceImpl implements SetDatadisConfigurationService {

    private final LockDatadisConfigRepository lockDatadisConfigRepository;
    private final GetDatadisConfigRepository getDatadisConfigRepository;
    private final SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    public SetDatadisConfigurationServiceImpl(LockDatadisConfigRepository lockDatadisConfigRepository,
                                              GetDatadisConfigRepository getDatadisConfigRepository,
                                              SetDatadisConfigurationRepository setDatadisConfigurationRepository) {
        this.lockDatadisConfigRepository = lockDatadisConfigRepository;
        this.getDatadisConfigRepository = getDatadisConfigRepository;
        this.setDatadisConfigurationRepository = setDatadisConfigurationRepository;
    }

    /**
     * The write never changes {@code enabled} (#425): a new configuration starts disabled, and an existing one keeps
     * its stored value, which only enabling and disabling change. The stored password is kept the same way when
     * none is supplied (#404).
     *
     * <p>Both are kept by writing back what was read, so the row is locked before it is read. Without the lock, an
     * enable or disable committed between this read and this write would be reverted by the write; with it, that
     * enable or disable waits for this transaction and applies on top of it.</p>
     */
    @Override
    public DatadisConfig setDatadisConfiguration(UUID communityId, SetDatadisConfiguration configuration) {
        Optional<DatadisConfig> stored = lockDatadisConfigRepository.lock(communityId)
                ? getDatadisConfigRepository.findByCommunityId(communityId)
                : Optional.empty();
        String password = configuration.getPassword().orElseGet(() -> storedPassword(stored, communityId));
        DatadisConfig config = new DatadisConfig.Builder()
                .setUsername(configuration.getUsername())
                .setPassword(password)
                .setBaseUrl(configuration.getBaseUrl())
                .setEnabled(stored.map(DatadisConfig::getEnabled).orElse(Boolean.FALSE))
                .build();
        return setDatadisConfigurationRepository.setDatadisConfiguration(communityId, config);
    }

    /**
     * A row may hold a null or empty password, written before the request body was validated. Keeping it
     * would leave the configuration without credentials, so only a non-empty password can be kept.
     */
    private String storedPassword(Optional<DatadisConfig> stored, UUID communityId) {
        return stored
                .map(DatadisConfig::getPassword)
                .filter(StringUtils::isNotEmpty)
                .orElseThrow(() -> new DatadisConfigPasswordRequiredException(communityId));
    }
}
