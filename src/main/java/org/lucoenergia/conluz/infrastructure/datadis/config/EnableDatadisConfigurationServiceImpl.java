package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.EnableDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.DatadisBaseUrlValidity;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigIncompleteException;
import org.lucoenergia.conluz.domain.datadis.DatadisConfigNotFoundException;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.domain.shared.config.ConnectionConfigCompleteness;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional
@Service
public class EnableDatadisConfigurationServiceImpl implements EnableDatadisConfigurationService {

    private final GetDatadisConfigRepository getDatadisConfigRepository;
    private final SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    private final DatadisBaseUrlValidity baseUrlValidity;

    public EnableDatadisConfigurationServiceImpl(GetDatadisConfigRepository getDatadisConfigRepository,
                                                 SetDatadisConfigurationRepository setDatadisConfigurationRepository,
                                                 DatadisBaseUrlValidity baseUrlValidity) {
        this.getDatadisConfigRepository = getDatadisConfigRepository;
        this.setDatadisConfigurationRepository = setDatadisConfigurationRepository;
        this.baseUrlValidity = baseUrlValidity;
    }

    /**
     * Reads the stored configuration in the same transaction as the write, so the data found complete is the
     * data enabled.
     */
    @Override
    public void enable(UUID communityId) {
        DatadisConfig config = getDatadisConfigRepository.findByCommunityId(communityId)
                .orElseThrow(() -> new DatadisConfigNotFoundException(communityId));
        List<String> missingFields = ConnectionConfigCompleteness.missingFields(
                config.getUsername(), config.getBaseUrl(), config.getPassword(), baseUrlValidity);
        if (!missingFields.isEmpty()) {
            throw new DatadisConfigIncompleteException(communityId, missingFields);
        }
        if (!setDatadisConfigurationRepository.setEnabled(communityId, true)) {
            throw new DatadisConfigNotFoundException(communityId);
        }
    }
}
