package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

public interface SetDatadisConfigurationService {

    /**
     * Creates or replaces the community's Datadis configuration. When no password is supplied, the
     * stored one is kept.
     *
     * @throws DatadisConfigPasswordRequiredException if no password is supplied and the community has
     *                                                no stored non-empty password to keep
     */
    DatadisConfig setDatadisConfiguration(UUID communityId, SetDatadisConfiguration configuration);
}
