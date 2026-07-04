package org.lucoenergia.conluz.domain.datadis.get;

import org.lucoenergia.conluz.domain.datadis.DatadisConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetDatadisConfigRepository {

    Optional<DatadisConfig> getDatadisConfig();

    Optional<DatadisConfig> findByCommunityId(UUID communityId);

    List<DatadisConfig> findAllEnabled();
}
