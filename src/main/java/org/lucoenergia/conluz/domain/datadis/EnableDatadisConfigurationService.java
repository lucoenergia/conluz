package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

public interface EnableDatadisConfigurationService {

    /**
     * Enables the community's Datadis configuration, changing nothing else. The stored configuration must be
     * complete. Enabling a configuration that is already enabled succeeds.
     *
     * @throws DatadisConfigNotFoundException   if the community has no configuration
     * @throws DatadisConfigIncompleteException if the stored configuration lacks a non-blank username, a valid
     *                                          base URL or a non-empty password
     */
    void enable(UUID communityId);
}
