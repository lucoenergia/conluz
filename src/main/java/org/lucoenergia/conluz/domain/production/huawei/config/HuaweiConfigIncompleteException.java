package org.lucoenergia.conluz.domain.production.huawei.config;

import java.util.List;
import java.util.UUID;

/**
 * The plant's Huawei configuration cannot be enabled because its stored data lacks fields a connection needs.
 */
public class HuaweiConfigIncompleteException extends RuntimeException {

    private final UUID plantId;
    private final List<String> missingFields;

    public HuaweiConfigIncompleteException(UUID plantId, List<String> missingFields) {
        super("Huawei configuration of plant " + plantId + " is missing " + missingFields);
        this.plantId = plantId;
        this.missingFields = List.copyOf(missingFields);
    }

    public UUID getPlantId() {
        return plantId;
    }

    /**
     * In the order {@code username}, {@code baseUrl}, {@code password}.
     */
    public List<String> getMissingFields() {
        return missingFields;
    }
}
