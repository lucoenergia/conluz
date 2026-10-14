package org.lucoenergia.conluz.domain.production.huawei.config;

import java.util.UUID;

/**
 * The plant has no Huawei configuration.
 */
public class HuaweiConfigNotFoundException extends RuntimeException {

    private final UUID plantId;

    public HuaweiConfigNotFoundException(UUID plantId) {
        this.plantId = plantId;
    }

    public UUID getPlantId() {
        return plantId;
    }
}
