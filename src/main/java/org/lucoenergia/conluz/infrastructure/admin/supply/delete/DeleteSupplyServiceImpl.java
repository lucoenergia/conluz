package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPartitionCoefficientsException;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPlantsException;
import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DeleteSupplyServiceImpl implements DeleteSupplyService {

    private final DeleteSupplyRepository repository;
    private final LockSupplyRepository lockSupplyRepository;
    private final GetPlantRepository getPlantRepository;
    private final GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository;

    public DeleteSupplyServiceImpl(DeleteSupplyRepository repository,
                                   LockSupplyRepository lockSupplyRepository,
                                   GetPlantRepository getPlantRepository,
                                   GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository) {
        this.repository = repository;
        this.lockSupplyRepository = lockSupplyRepository;
        this.getPlantRepository = getPlantRepository;
        this.getSupplyPartitionCoefficientRepository = getSupplyPartitionCoefficientRepository;
    }

    /**
     * The supply row is locked first, exclusively: a plant or coefficient being inserted for it is waited for and
     * then seen by the checks, so the deletion is refused rather than failing on a foreign key.
     */
    @Override
    public void delete(SupplyId id) {
        if (!lockSupplyRepository.lock(id)) {
            throw new SupplyNotFoundException(id);
        }
        if (getPlantRepository.existsBySupply(id)) {
            throw new SupplyHasPlantsException(id);
        }
        if (getSupplyPartitionCoefficientRepository.existsBySupply(id)) {
            throw new SupplyHasPartitionCoefficientsException(id);
        }
        repository.delete(id);
    }
}
