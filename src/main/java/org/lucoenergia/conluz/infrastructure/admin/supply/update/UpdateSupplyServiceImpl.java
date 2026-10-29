package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyCodeAvailability;
import org.lucoenergia.conluz.domain.admin.supply.SupplyCodeInvalidException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyDto;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Service
public class UpdateSupplyServiceImpl implements UpdateSupplyService {

    private final GetSupplyRepository getSupplyRepository;
    private final UpdateSupplyRepository updateSupplyRepository;
    private final SupplyCodeAvailability supplyCodeAvailability;

    public UpdateSupplyServiceImpl(GetSupplyRepository getSupplyRepository,
                                   UpdateSupplyRepository updateSupplyRepository,
                                   SupplyCodeAvailability supplyCodeAvailability) {
        this.getSupplyRepository = getSupplyRepository;
        this.updateSupplyRepository = updateSupplyRepository;
        this.supplyCodeAvailability = supplyCodeAvailability;
    }

    @Override
    public Supply update(SupplyId supplyId, UpdateSupplyDto updateSupplyDto) {

        Optional<Supply> supplyOptional = getSupplyRepository.findById(supplyId);
        if (supplyOptional.isEmpty()) {
            throw new SupplyNotFoundException(supplyId);
        }
        Supply stored = supplyOptional.get();

        Supply supplyToUpdate = updateSupplyDto.mapToSupply(stored.copy())
                .copy()
                .withCode(codeToStore(stored, updateSupplyDto.getCode()))
                .build();

        return updateSupplyRepository.update(supplyToUpdate);
    }

    /**
     * An unchanged code is kept as it is stored, even one written before the CUPS rule (#532), so that such a supply
     * can still be edited. A changed one is checked as a new supply's code is, leaving the supply itself out.
     */
    private String codeToStore(Supply stored, String submitted) {
        if (submitted.equals(stored.getCode())) {
            return stored.getCode();
        }
        SupplyCode code = SupplyCode.ofCups(submitted).orElseThrow(SupplyCodeInvalidException::new);
        supplyCodeAvailability.requireAvailable(code, stored.getCommunity().getId(), SupplyId.of(stored.getId()));
        return code.getCode();
    }
}
