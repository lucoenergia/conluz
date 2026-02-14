package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyDto;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Service
public class UpdateSupplyServiceImpl implements UpdateSupplyService {

    private final GetSupplyRepository getSupplyRepository;
    private final UpdateSupplyRepository updateSupplyRepository;

    public UpdateSupplyServiceImpl(GetSupplyRepository getSupplyRepository,
                                   UpdateSupplyRepository updateSupplyRepository) {
        this.getSupplyRepository = getSupplyRepository;
        this.updateSupplyRepository = updateSupplyRepository;
    }

    @Override
    public Supply update(SupplyId supplyId, UpdateSupplyDto updateSupplyDto) {

        Optional<Supply> supplyOptional = getSupplyRepository.findById(supplyId);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyId);
        }

        Supply supplyToUpdate = updateSupplyDto.mapToSupply(supplyOptional.get().copy());

        return updateSupplyRepository.update(supplyToUpdate);
    }
}
