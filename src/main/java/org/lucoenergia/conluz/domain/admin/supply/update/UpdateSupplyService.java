package org.lucoenergia.conluz.domain.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class UpdateSupplyService {

    private final UpdateSupplyRepository repository;

    public UpdateSupplyService(UpdateSupplyRepository repository) {
        this.repository = repository;
    }

    public Supply update(Supply supply) {
        return repository.update(supply);
    }
}
