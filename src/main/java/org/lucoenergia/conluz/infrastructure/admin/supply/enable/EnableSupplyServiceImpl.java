package org.lucoenergia.conluz.infrastructure.admin.supply.enable;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.enable.EnableSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.enable.EnableSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class EnableSupplyServiceImpl implements EnableSupplyService {

    private final EnableSupplyRepository repository;

    public EnableSupplyServiceImpl(EnableSupplyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Supply enable(SupplyId id) {
        return repository.enable(id);
    }
}
