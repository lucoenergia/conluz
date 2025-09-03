package org.lucoenergia.conluz.infrastructure.admin.supply.disable;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.disable.DisableSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.disable.DisableSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DisableSupplyServiceImpl implements DisableSupplyService {

    private final DisableSupplyRepository repository;

    public DisableSupplyServiceImpl(DisableSupplyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Supply disable(SupplyId id) {
        return repository.disable(id);
    }
}
