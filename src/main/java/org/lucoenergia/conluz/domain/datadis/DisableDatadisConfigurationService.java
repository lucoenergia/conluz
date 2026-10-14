package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

public interface DisableDatadisConfigurationService {

    /**
     * Disables the community's Datadis configuration, changing nothing else, whatever the stored configuration
     * holds. Disabling a configuration that is already disabled succeeds.
     *
     * @throws DatadisConfigNotFoundException if the community has no configuration
     */
    void disable(UUID communityId);
}
