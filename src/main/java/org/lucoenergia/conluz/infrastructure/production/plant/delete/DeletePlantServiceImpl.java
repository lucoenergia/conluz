package org.lucoenergia.conluz.infrastructure.production.plant.delete;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.DeleteHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantService;
import org.lucoenergia.conluz.domain.production.plant.delete.PlantHasPartitionCoefficientsException;
import org.lucoenergia.conluz.domain.production.plant.delete.PlantHasSharingAgreementsException;
import org.lucoenergia.conluz.domain.production.plant.lock.LockPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DeletePlantServiceImpl implements DeletePlantService {

    private final DeletePlantRepository repository;
    private final LockPlantRepository lockPlantRepository;
    private final GetSharingAgreementRepository getSharingAgreementRepository;
    private final GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository;
    private final DeleteHuaweiConfigRepository deleteHuaweiConfigRepository;

    public DeletePlantServiceImpl(DeletePlantRepository repository,
                                  LockPlantRepository lockPlantRepository,
                                  GetSharingAgreementRepository getSharingAgreementRepository,
                                  GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository,
                                  DeleteHuaweiConfigRepository deleteHuaweiConfigRepository) {
        this.repository = repository;
        this.lockPlantRepository = lockPlantRepository;
        this.getSharingAgreementRepository = getSharingAgreementRepository;
        this.getSupplyPartitionCoefficientRepository = getSupplyPartitionCoefficientRepository;
        this.deleteHuaweiConfigRepository = deleteHuaweiConfigRepository;
    }

    /**
     * The plant row is locked first, exclusively: a sharing agreement or coefficient being inserted for it is waited
     * for and then seen by the checks, so the deletion is refused rather than failing on a foreign key.
     */
    @Override
    public void delete(PlantId id) {
        if (!lockPlantRepository.lock(id)) {
            throw new PlantNotFoundException(id);
        }
        if (getSharingAgreementRepository.existsByPlant(id)) {
            throw new PlantHasSharingAgreementsException(id);
        }
        if (getSupplyPartitionCoefficientRepository.existsByPlant(id)) {
            throw new PlantHasPartitionCoefficientsException(id);
        }
        deleteHuaweiConfigRepository.deleteByPlant(id);
        repository.delete(id);
    }
}
