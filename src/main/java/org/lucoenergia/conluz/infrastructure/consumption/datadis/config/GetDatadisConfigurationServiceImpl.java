package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetDatadisConfigurationServiceImpl implements GetDatadisConfigurationService {

    private final GetDatadisConfigRepository getDatadisConfigRepository;

    public GetDatadisConfigurationServiceImpl(GetDatadisConfigRepository getDatadisConfigRepository) {
        this.getDatadisConfigRepository = getDatadisConfigRepository;
    }

    @Override
    public Optional<DatadisConfig> findByCommunityId(UUID communityId) {
        return getDatadisConfigRepository.findByCommunityId(communityId);
    }

    @Override
    public List<DatadisConfig> findAllEnabled() {
        return getDatadisConfigRepository.findAllEnabled();
    }
}
