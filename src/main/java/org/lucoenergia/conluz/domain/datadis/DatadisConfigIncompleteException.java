package org.lucoenergia.conluz.domain.datadis;

import java.util.List;
import java.util.UUID;

/**
 * The community's Datadis configuration cannot be enabled because its stored data lacks fields a connection needs.
 */
public class DatadisConfigIncompleteException extends RuntimeException {

    private final UUID communityId;
    private final List<String> missingFields;

    public DatadisConfigIncompleteException(UUID communityId, List<String> missingFields) {
        super("Datadis configuration of community " + communityId + " is missing " + missingFields);
        this.communityId = communityId;
        this.missingFields = List.copyOf(missingFields);
    }

    public UUID getCommunityId() {
        return communityId;
    }

    /**
     * In the order {@code username}, {@code baseUrl}, {@code password}.
     */
    public List<String> getMissingFields() {
        return missingFields;
    }
}
