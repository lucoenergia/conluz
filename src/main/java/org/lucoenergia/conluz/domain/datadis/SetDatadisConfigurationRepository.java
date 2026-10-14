package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

public interface SetDatadisConfigurationRepository {

    DatadisConfig setDatadisConfiguration(DatadisConfig config);

    DatadisConfig setDatadisConfiguration(UUID communityId, DatadisConfig config);

    /**
     * Sets only whether the community's configuration is enabled, leaving every other field as stored.
     *
     * @return {@code false} when the community has no configuration, in which case nothing is written
     */
    boolean setEnabled(UUID communityId, boolean enabled);
}
