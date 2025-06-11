package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class UpdateSupplyServiceImpl implements UpdateSupplyService {

    private final UpdateSupplyRepository repository;

    public UpdateSupplyServiceImpl(UpdateSupplyRepository repository) {
        this.repository = repository;
    }

    public Supply update(Supply supply) {
        return repository.update(supply);
    }
}
