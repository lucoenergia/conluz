package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

public interface SetDatadisConfigurationEnabledService {

    /**
     * Enables or disables the community's Datadis configuration, changing nothing else. Disabling always
     * succeeds; enabling requires the stored configuration to be complete. Setting the value it already has
     * succeeds.
     *
     * @throws DatadisConfigNotFoundException   if the community has no configuration
     * @throws DatadisConfigIncompleteException if enabling and the stored configuration lacks a non-blank
     *                                          username, a valid base URL or a non-empty password
     */
    void setEnabled(UUID communityId, boolean enabled);
}
