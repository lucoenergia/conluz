package org.lucoenergia.conluz.domain.production.huawei.config;

import java.util.UUID;

/**
 * A Huawei configuration was set without a password for a plant that has no stored password to keep.
 */
public class HuaweiConfigPasswordRequiredException extends RuntimeException {

    private final UUID plantId;

    public HuaweiConfigPasswordRequiredException(UUID plantId) {
        this.plantId = plantId;
    }

    public UUID getPlantId() {
        return plantId;
    }
}
