package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

/**
 * The community has no Datadis configuration.
 */
public class DatadisConfigNotFoundException extends RuntimeException {

    private final UUID communityId;

    public DatadisConfigNotFoundException(UUID communityId) {
        this.communityId = communityId;
    }

    public UUID getCommunityId() {
        return communityId;
    }
}
