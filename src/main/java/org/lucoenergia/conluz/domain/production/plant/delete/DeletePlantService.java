package org.lucoenergia.conluz.domain.production.plant.delete;

import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.shared.PlantId;

public interface DeletePlantService {

    /**
     * Deletes the plant with its Huawei configuration (PLT-001). Its production series in the time-series database
     * are kept.
     *
     * @throws PlantNotFoundException                  if there is no such plant
     * @throws PlantHasSharingAgreementsException      if a sharing agreement, in any status, references the plant;
     *                                                 nothing is deleted
     * @throws PlantHasPartitionCoefficientsException  if a partition coefficient references the plant; nothing is
     *                                                 deleted
     */
    void delete(PlantId id);
}
