package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

/**
 * A Datadis configuration was set without a password for a community that has no stored password to keep.
 */
public class DatadisConfigPasswordRequiredException extends RuntimeException {

    private final UUID communityId;

    public DatadisConfigPasswordRequiredException(UUID communityId) {
        this.communityId = communityId;
    }

    public UUID getCommunityId() {
        return communityId;
    }
}
