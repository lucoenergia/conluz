package org.lucoenergia.conluz.infrastructure.admin.supply.lock;

import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LockSupplyRepositoryDatabase implements LockSupplyRepository {

    private final SupplyRepository repository;

    public LockSupplyRepositoryDatabase(SupplyRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean lock(SupplyId supplyId) {
        return repository.lockById(supplyId.getId()).isPresent();
    }

    @Override
    public boolean lockAsReferenced(SupplyId supplyId) {
        return repository.lockAsReferencedById(supplyId.getId()).isPresent();
    }
}
