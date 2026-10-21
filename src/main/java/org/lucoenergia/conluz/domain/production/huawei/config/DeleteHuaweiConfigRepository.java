package org.lucoenergia.conluz.domain.production.huawei.config;

import org.lucoenergia.conluz.domain.shared.PlantId;

public interface DeleteHuaweiConfigRepository {

    /**
     * Deletes the plant's Huawei configuration. A plant without one is left as it is.
     */
    void deleteByPlant(PlantId plantId);
}
