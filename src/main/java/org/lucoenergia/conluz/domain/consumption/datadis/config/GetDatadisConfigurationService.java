package org.lucoenergia.conluz.domain.consumption.datadis.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetDatadisConfigurationService {

    Optional<DatadisConfig> findByCommunityId(UUID communityId);

    List<DatadisConfig> findAllEnabled();
}
